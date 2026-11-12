package com.moyeorock.domain.song.service;

import com.moyeorock.domain.performance.dto.response.PerformanceBriefResponse;
import com.moyeorock.domain.setlist.dto.response.TeamSongResponse;
import com.moyeorock.domain.setlist.service.SetlistService;
import com.moyeorock.domain.song.client.AiSongRecommendRequest;
import com.moyeorock.domain.song.client.AiSongRecommendResponse;
import com.moyeorock.domain.song.client.AiSongRecommendationClient;
import com.moyeorock.domain.song.dto.response.SongDifficultyResponse;
import com.moyeorock.domain.song.dto.response.SongRecommendationResponse;
import com.moyeorock.domain.song.dto.response.SongResponse;
import com.moyeorock.domain.song.dto.response.TeamSongRecommendationResponse;
import com.moyeorock.domain.song.dto.response.TeamSongRecommendationResponse.RecommendedSong;
import com.moyeorock.domain.team.dto.response.TeamDetailResponse;
import com.moyeorock.domain.team.dto.response.TeamMemberResponse;
import com.moyeorock.domain.team.dto.response.TeamSummaryResponse;
import com.moyeorock.domain.team.service.TeamMemberService;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.domain.user.dto.response.UserInstrumentResponse;
import com.moyeorock.domain.user.dto.response.UserMeResponse;
import com.moyeorock.domain.user.service.UserService;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * 곡 추천(AI, 팀 #171 · 개인 #172). 팀 정보·팀원 실력(또는 내 장르·실력)을 모아 AI에 보내고, AI가 자기 카탈로그에서 고른 곡({@code externalId})을
 * 우리 곡으로 바꿔 돌려준다.
 *
 * <p>SongService와 나눈 이유: 호출 방향이 {@code TeamService → SetlistService → SongService}라서 SongService가
 * TeamService를 부르면 순환이 생긴다. UserActivityService(#90)처럼 같은 도메인 안의 별도 Service로 둔다(architecture.md §3).
 * 이 Service를 주입받는 다른 Service는 없다.
 *
 * <p>다른 도메인 Service를 부르고 AI 응답을 기다리므로 트랜잭션을 붙이지 않는다. DB 커넥션을 잡고 기다리지 않게 하고,
 * 호출된 Service가 자기 트랜잭션을 연다(TeamService.recommendMembers와 같은 이유).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SongRecommendationService {

    /** 팀 추천 응답 곡 수. 프론트 셋리스트 곡 추가 시트에 보여 준다. */
    static final int TEAM_RESULT_LIMIT = 10;
    /** 팀 추천에서 AI에 요청하는 곡 수. 우리 DB에 아직 없는 곡을 버려도 {@link #TEAM_RESULT_LIMIT}곡이 남도록 넉넉히 받는다. */
    static final int TEAM_AI_REQUEST_LIMIT = 20;
    /** 개인 추천 응답 곡 수. 프론트 홈 "취향 참고 추천곡"에 2곡을 보여 준다(2026-11-12 결정, 이슈 #172). */
    static final int PERSONAL_RESULT_LIMIT = 2;
    /** 개인 추천에서 AI에 요청하는 곡 수. 우리 DB에 없는 곡을 버려도 {@link #PERSONAL_RESULT_LIMIT}곡이 남도록 넉넉히 받는다. */
    static final int PERSONAL_AI_REQUEST_LIMIT = 10;

    // 추천 근거 문구는 화면에 그대로 나간다. 프론트 표시명(domainCodes.ts)과 맞춘다.
    private static final Map<Genre, String> GENRE_LABELS = new EnumMap<>(Map.ofEntries(
            Map.entry(Genre.ROCK, "록"), Map.entry(Genre.INDIE, "인디"), Map.entry(Genre.PUNK, "펑크"),
            Map.entry(Genre.METAL, "메탈"), Map.entry(Genre.POP, "팝"), Map.entry(Genre.BALLAD, "발라드"),
            Map.entry(Genre.JAZZ, "재즈"), Map.entry(Genre.HIPHOP, "힙합"), Map.entry(Genre.RNB, "R&B"),
            Map.entry(Genre.ACOUSTIC, "어쿠스틱"), Map.entry(Genre.ETC, "기타")));
    private static final Map<Instrument, String> PART_LABELS = new EnumMap<>(Map.of(
            Instrument.VOCAL, "보컬", Instrument.EL_GT, "일렉", Instrument.AC_GT, "어쿠스틱",
            Instrument.BASS, "베이스", Instrument.DRUM, "드럼", Instrument.KEY, "키보드"));

    private final TeamMemberService teamMemberService;
    private final TeamService teamService;
    private final UserService userService;
    private final SetlistService setlistService;
    private final SongService songService;
    private final AiSongRecommendationClient aiSongRecommendationClient;

    /**
     * 팀 곡 추천. 활동 중인 팀원만 부를 수 있다. 없는 팀·해체된 팀은 TEAM_NOT_FOUND(404), 팀원이 아니면 NO_PERMISSION(403)
     * (팀 선택곡 조회와 같은 규칙, TeamMemberService.ensureActiveMember).
     *
     * <p>AI에는 팀 장르와 팀원마다 이 팀에서 맡은 세션·그 악기의 실력을 보낸다. 실력을 모르는 팀원은 뺀다.
     * AI가 고른 곡 중 우리 DB에 없는 곡은 버리고 AI 순서대로 최대 {@value #TEAM_RESULT_LIMIT}곡을 돌려준다.
     * AI 주소가 없거나 호출이 실패하면 SONG_RECOMMENDATION_UNAVAILABLE(503)이다.
     */
    public TeamSongRecommendationResponse recommendForTeam(Long userId, Long teamId) {
        teamMemberService.ensureActiveMember(teamId, userId);
        TeamDetailResponse team = teamService.getDetail(userId, teamId);
        List<Genre> genres = team.genres() == null ? List.of() : team.genres();
        Map<Instrument, Level> bestLevels = new EnumMap<>(Instrument.class);
        List<AiSongRecommendRequest.Member> members = toRecommendationMembers(team.members(), bestLevels);

        AiSongRecommendResponse response = callRecommendation(
                AiSongRecommendRequest.forTeam(genres, members, TEAM_AI_REQUEST_LIMIT), "teamId=" + teamId);
        Set<Long> selectedByOtherTeams = findSongIdsSelectedByOtherTeams(team.performance(), teamId);

        List<RecommendedSong> recommended = resolveSongs(response, TEAM_RESULT_LIMIT).stream()
                .map(scored -> new RecommendedSong(scored.song(), scored.score(),
                        reasons(scored.song(), genres, bestLevels), selectedByOtherTeams.contains(scored.song().id())))
                .toList();
        return new TeamSongRecommendationResponse(teamId, recommended);
    }

    /**
     * 내 취향 추천곡(이슈 #172). 프론트 홈의 "추천받기" 버튼이 부른다. 탈퇴했거나 없는 사용자는 USER_NOT_FOUND(404)
     * (UserService.getMe).
     *
     * <p>온보딩을 마치지 않은 사용자는 AI를 부르지 않고 빈 목록이다(2026-11-12 결정). AI에는 내 선호 장르와 악기별 실력을
     * 보낸다. AI가 고른 곡 중 우리 DB에 없는 곡은 버리고 AI 순서대로 최대 {@value #PERSONAL_RESULT_LIMIT}곡을 돌려준다.
     * AI 주소가 없거나 호출이 실패하면 SONG_RECOMMENDATION_UNAVAILABLE(503)이다.
     */
    public SongRecommendationResponse recommendForUser(Long userId) {
        UserMeResponse me = userService.getMe(userId);
        if (!me.onboardingCompleted()) {
            return new SongRecommendationResponse(List.of());
        }
        List<Genre> genres = me.genres() == null ? List.of() : me.genres();
        List<AiSongRecommendRequest.Member> members = me.instruments() == null ? List.of() : me.instruments().stream()
                .filter(i -> i.instrument() != null && i.level() != null)
                .map(i -> new AiSongRecommendRequest.Member(i.instrument(), i.level()))
                .toList();

        AiSongRecommendResponse response = callRecommendation(
                AiSongRecommendRequest.forPersonal(genres, members, PERSONAL_AI_REQUEST_LIMIT), "userId=" + userId);

        return new SongRecommendationResponse(resolveSongs(response, PERSONAL_RESULT_LIMIT).stream()
                .map(scored -> new SongRecommendationResponse.RecommendedSong(scored.song(), scored.score()))
                .toList());
    }

    // AI가 고른 곡을 우리 곡으로 바꾼다. 우리 DB에 없는 곡은 버리고 AI 순서(점수 높은 순)대로 최대 limit곡이다.
    private List<ScoredSong> resolveSongs(AiSongRecommendResponse response, int limit) {
        List<AiSongRecommendResponse.Item> items = validItems(response);
        Map<String, SongResponse> songs = songService.findByExternalIds(
                items.stream().map(AiSongRecommendResponse.Item::externalId).toList());
        return items.stream()
                .filter(item -> songs.containsKey(item.externalId()))
                .limit(limit)
                .map(item -> new ScoredSong(songs.get(item.externalId()), item.score()))
                .toList();
    }

    private record ScoredSong(SongResponse song, double score) {
    }

    // 팀원마다 이 팀에서 맡은 세션과 그 악기의 실력을 붙인다. 실력·세션을 모르는 팀원은 AI 요청에서 뺀다(AI가 422를 준다).
    // 추천 근거용으로 세션별 가장 높은 실력도 함께 모은다. 같은 세션이 둘이면 잘하는 사람이 맡을 수 있다.
    private List<AiSongRecommendRequest.Member> toRecommendationMembers(List<TeamMemberResponse> members,
            Map<Instrument, Level> bestLevels) {
        List<TeamMemberResponse> known = members == null ? List.of() : members.stream()
                .filter(m -> m.user() != null && m.instrument() != null)
                .toList();
        Map<Long, List<UserInstrumentResponse>> instruments =
                userService.getInstruments(known.stream().map(m -> m.user().id()).distinct().toList());

        List<AiSongRecommendRequest.Member> result = new ArrayList<>();
        for (TeamMemberResponse member : known) {
            Level level = instruments.getOrDefault(member.user().id(), List.of()).stream()
                    .filter(i -> i.instrument() == member.instrument())
                    .map(UserInstrumentResponse::level)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (level == null) {
                continue;
            }
            result.add(new AiSongRecommendRequest.Member(member.instrument(), level));
            bestLevels.merge(member.instrument(), level, (a, b) -> a.compareTo(b) >= 0 ? a : b);
        }
        return result;
    }

    // target은 로그용(teamId=5, userId=3)이다.
    private AiSongRecommendResponse callRecommendation(AiSongRecommendRequest request, String target) {
        if (!aiSongRecommendationClient.isConfigured()) {
            log.warn("AI 추천 서버 주소가 설정되지 않아 곡 추천을 할 수 없습니다.");
            throw new BusinessException(ErrorCode.SONG_RECOMMENDATION_UNAVAILABLE);
        }
        try {
            return aiSongRecommendationClient.recommend(request);
        } catch (RestClientException | IllegalStateException e) {
            log.warn("AI 서버 곡 추천 호출에 실패했습니다. mode={} {}", request.mode(), target, e);
            throw new BusinessException(ErrorCode.SONG_RECOMMENDATION_UNAVAILABLE);
        }
    }

    // externalId·점수가 없는 항목과 같은 externalId의 뒤쪽 항목은 버린다. 순서는 AI가 준 그대로(점수 높은 순)다.
    private List<AiSongRecommendResponse.Item> validItems(AiSongRecommendResponse response) {
        Map<String, AiSongRecommendResponse.Item> unique = new LinkedHashMap<>();
        response.songs().stream()
                .filter(item -> item != null && item.externalId() != null && !item.externalId().isBlank()
                        && item.score() != null)
                .forEach(item -> unique.putIfAbsent(item.externalId(), item));
        return List.copyOf(unique.values());
    }

    // 같은 공연의 다른(활동 중인) 팀이 선택곡으로 고른 곡 id. 독립 팀이면 비교할 팀이 없다.
    private Set<Long> findSongIdsSelectedByOtherTeams(PerformanceBriefResponse performance, Long teamId) {
        if (performance == null) {
            return Set.of();
        }
        return teamService.getActiveSummaries(performance.id()).stream()
                .map(TeamSummaryResponse::id)
                .filter(id -> !id.equals(teamId))
                .flatMap(id -> setlistService.findTeamSongs(id).stream())
                .map(TeamSongResponse::song)
                .filter(Objects::nonNull)
                .map(song -> song.id())
                .collect(Collectors.toSet());
    }

    // 추천 근거. 팀 장르와 곡 장르가 같으면 장르 일치, 팀이 가진 세션마다 곡 난이도와 그 세션의 가장 높은 실력을 비교한다.
    // 곡에 그 세션 난이도 정보가 없으면 건너뛴다.
    private List<String> reasons(SongResponse song, List<Genre> teamGenres, Map<Instrument, Level> bestLevels) {
        List<String> reasons = new ArrayList<>();
        if (song.genre() != null && teamGenres.contains(song.genre())) {
            reasons.add("팀 장르 일치: " + GENRE_LABELS.get(song.genre()));
        }
        for (SongDifficultyResponse difficulty : song.difficulty()) {
            Level teamLevel = bestLevels.get(difficulty.part());
            if (teamLevel == null || difficulty.level() == null) {
                continue;
            }
            String part = PART_LABELS.get(difficulty.part());
            reasons.add(teamLevel.compareTo(difficulty.level()) >= 0 ? part + " 실력 충족" : part + " 난이도 초과");
        }
        return List.copyOf(reasons);
    }
}
