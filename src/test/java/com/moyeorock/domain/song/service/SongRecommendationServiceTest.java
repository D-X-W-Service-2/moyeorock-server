package com.moyeorock.domain.song.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moyeorock.domain.performance.dto.response.PerformanceBriefResponse;
import com.moyeorock.domain.setlist.dto.response.TeamSongResponse;
import com.moyeorock.domain.setlist.service.SetlistService;
import com.moyeorock.domain.song.client.AiSongRecommendRequest;
import com.moyeorock.domain.song.client.AiSongRecommendResponse;
import com.moyeorock.domain.song.client.AiSongRecommendationClient;
import com.moyeorock.domain.song.dto.response.SongDifficultyResponse;
import com.moyeorock.domain.song.dto.response.SongRecommendationResponse;
import com.moyeorock.domain.song.dto.response.SongResponse;
import com.moyeorock.domain.song.dto.response.SongSummaryResponse;
import com.moyeorock.domain.song.dto.response.TeamSongRecommendationResponse;
import com.moyeorock.domain.song.dto.response.TeamSongRecommendationResponse.RecommendedSong;
import com.moyeorock.domain.team.dto.response.TeamDetailResponse;
import com.moyeorock.domain.team.dto.response.TeamMemberResponse;
import com.moyeorock.domain.team.dto.response.TeamSummaryResponse;
import com.moyeorock.domain.team.enums.MemberStatus;
import com.moyeorock.domain.team.enums.TeamRole;
import com.moyeorock.domain.team.enums.TeamStatus;
import com.moyeorock.domain.team.service.TeamMemberService;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.domain.user.dto.response.UserInstrumentResponse;
import com.moyeorock.domain.user.dto.response.UserMeResponse;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import com.moyeorock.domain.user.enums.LoginType;
import com.moyeorock.domain.user.enums.PlatformRole;
import com.moyeorock.domain.user.service.UserService;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class SongRecommendationServiceTest {

    private static final Long TEAM_ID = 5L;
    private static final Long OTHER_TEAM_ID = 6L;
    private static final Long PERFORMANCE_ID = 30L;
    private static final Long LEADER_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final Long NO_LEVEL_MEMBER_ID = 3L;

    @Mock
    TeamMemberService teamMemberService;
    @Mock
    TeamService teamService;
    @Mock
    UserService userService;
    @Mock
    SetlistService setlistService;
    @Mock
    SongService songService;
    @Mock
    AiSongRecommendationClient aiSongRecommendationClient;

    @InjectMocks
    SongRecommendationService service;

    // ---------- 고정 데이터 ----------

    private static TeamMemberResponse member(Long userId, TeamRole role, Instrument instrument) {
        return new TeamMemberResponse(new UserSummaryResponse(userId, "u" + userId, null), role, instrument,
                MemberStatus.ACTIVE, LocalDateTime.now());
    }

    // 팀장은 보컬(중급), 팀원은 드럼(초급), 셋째는 베이스지만 베이스 실력을 등록하지 않았다(기타 실력만 있다).
    private static TeamDetailResponse team(PerformanceBriefResponse performance) {
        return new TeamDetailResponse(TEAM_ID, "무명밴드", null, Region.SEOUL, List.of(Genre.ROCK), TeamStatus.ACTIVE,
                performance, null, 3,
                List.of(member(LEADER_ID, TeamRole.LEADER, Instrument.VOCAL),
                        member(MEMBER_ID, TeamRole.MEMBER, Instrument.DRUM),
                        member(NO_LEVEL_MEMBER_ID, TeamRole.MEMBER, Instrument.BASS)),
                List.of(), TeamRole.LEADER, LocalDateTime.now());
    }

    private static SongResponse song(long id, Genre genre, List<SongDifficultyResponse> difficulty) {
        return new SongResponse(id, "곡" + id, "가수" + id, genre, null, null, difficulty);
    }

    private static AiSongRecommendResponse.Item item(String externalId, Double score) {
        return new AiSongRecommendResponse.Item(externalId, score);
    }

    private void givenTeam(PerformanceBriefResponse performance) {
        given(teamService.getDetail(LEADER_ID, TEAM_ID)).willReturn(team(performance));
        given(userService.getInstruments(List.of(LEADER_ID, MEMBER_ID, NO_LEVEL_MEMBER_ID))).willReturn(Map.of(
                LEADER_ID, List.of(new UserInstrumentResponse(11L, Instrument.VOCAL, Level.INTERMEDIATE)),
                MEMBER_ID, List.of(new UserInstrumentResponse(12L, Instrument.DRUM, Level.BEGINNER),
                        new UserInstrumentResponse(13L, Instrument.VOCAL, Level.ADVANCED)),
                NO_LEVEL_MEMBER_ID, List.of(new UserInstrumentResponse(14L, Instrument.EL_GT, Level.ADVANCED))));
        given(aiSongRecommendationClient.isConfigured()).willReturn(true);
    }

    private void givenAi(List<AiSongRecommendResponse.Item> items) {
        given(aiSongRecommendationClient.recommend(any()))
                .willReturn(new AiSongRecommendResponse(items, "content_rule"));
    }

    // ---------- 검증 ----------

    @Test
    @DisplayName("팀원이 아니면 TeamMemberService가 던진 403을 그대로 내고 팀 조회도 AI 호출도 하지 않는다")
    void notMember_forbidden() {
        willThrow(new BusinessException(ErrorCode.NO_PERMISSION))
                .given(teamMemberService).ensureActiveMember(TEAM_ID, MEMBER_ID);

        assertThatThrownBy(() -> service.recommendForTeam(MEMBER_ID, TEAM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NO_PERMISSION);
        verify(teamService, never()).getDetail(any(), any());
        verify(aiSongRecommendationClient, never()).recommend(any());
    }

    // ---------- AI 요청 ----------

    @Test
    @DisplayName("AI에는 TEAM 모드로 팀 장르와 팀원마다 맡은 세션·그 악기 실력을 보내고, 실력을 모르는 팀원은 뺀다")
    void sendsTeamGenresAndMemberLevels() {
        givenTeam(null);
        givenAi(List.of());
        given(songService.findByExternalIds(anyCollection())).willReturn(Map.of());

        service.recommendForTeam(LEADER_ID, TEAM_ID);

        ArgumentCaptor<AiSongRecommendRequest> captor = ArgumentCaptor.forClass(AiSongRecommendRequest.class);
        verify(aiSongRecommendationClient).recommend(captor.capture());
        AiSongRecommendRequest sent = captor.getValue();
        assertThat(sent.mode()).isEqualTo("TEAM");
        assertThat(sent.genres()).containsExactly(Genre.ROCK);
        // 팀원의 보컬 실력(ADVANCED)이 아니라 이 팀에서 맡은 드럼 실력을 보낸다. 베이스 실력이 없는 셋째는 빠진다.
        assertThat(sent.members()).containsExactly(
                new AiSongRecommendRequest.Member(Instrument.VOCAL, Level.INTERMEDIATE),
                new AiSongRecommendRequest.Member(Instrument.DRUM, Level.BEGINNER));
        assertThat(sent.limit()).isEqualTo(SongRecommendationService.TEAM_AI_REQUEST_LIMIT);
    }

    // ---------- 응답 조립 ----------

    @Test
    @DisplayName("AI 순서를 지키고, 우리 DB에 없는 곡·점수 없는 항목·중복 externalId는 버린다")
    void resolvesSongsInAiOrder() {
        givenTeam(null);
        givenAi(List.of(item("ext-2", 0.9), item("ext-missing", 0.85), item("ext-1", 0.8),
                item("ext-no-score", null), item("ext-2", 0.1)));
        given(songService.findByExternalIds(List.of("ext-2", "ext-missing", "ext-1"))).willReturn(Map.of(
                "ext-1", song(101L, Genre.POP, List.of()),
                "ext-2", song(102L, Genre.POP, List.of())));

        TeamSongRecommendationResponse response = service.recommendForTeam(LEADER_ID, TEAM_ID);

        assertThat(response.teamId()).isEqualTo(TEAM_ID);
        assertThat(response.songs()).extracting(r -> r.song().id()).containsExactly(102L, 101L);
        assertThat(response.songs()).extracting(RecommendedSong::score).containsExactly(0.9, 0.8);
    }

    @Test
    @DisplayName("AI가 많이 골라도 최대 10곡만 돌려준다")
    void limitsToTenSongs() {
        givenTeam(null);
        List<AiSongRecommendResponse.Item> items = IntStream.rangeClosed(1, 15)
                .mapToObj(i -> item("ext-" + i, 1.0 - i * 0.01)).toList();
        givenAi(items);
        Map<String, SongResponse> songs = new java.util.HashMap<>();
        IntStream.rangeClosed(1, 15).forEach(i -> songs.put("ext-" + i, song(100L + i, Genre.POP, List.of())));
        given(songService.findByExternalIds(anyCollection())).willReturn(songs);

        TeamSongRecommendationResponse response = service.recommendForTeam(LEADER_ID, TEAM_ID);

        assertThat(response.songs()).hasSize(SongRecommendationService.TEAM_RESULT_LIMIT);
        assertThat(response.songs().get(0).song().id()).isEqualTo(101L);
    }

    @Test
    @DisplayName("근거: 팀 장르 일치, 팀 세션마다 실력 충족·난이도 초과, 팀에 없는 세션과 난이도 정보 없는 세션은 문구가 없다")
    void buildsReasons() {
        givenTeam(null);
        givenAi(List.of(item("ext-1", 0.9), item("ext-2", 0.8)));
        given(songService.findByExternalIds(anyCollection())).willReturn(Map.of(
                "ext-1", song(101L, Genre.ROCK, List.of(
                        new SongDifficultyResponse(Instrument.VOCAL, Level.INTERMEDIATE),
                        new SongDifficultyResponse(Instrument.BASS, Level.NOVICE),
                        new SongDifficultyResponse(Instrument.DRUM, Level.ADVANCED))),
                "ext-2", song(102L, Genre.POP, List.of())));

        TeamSongRecommendationResponse response = service.recommendForTeam(LEADER_ID, TEAM_ID);

        // 베이스는 실력을 모르는 팀원만 맡고 있어 비교하지 않는다.
        assertThat(response.songs().get(0).reasons())
                .containsExactly("팀 장르 일치: 록", "보컬 실력 충족", "드럼 난이도 초과");
        assertThat(response.songs().get(1).reasons()).isEmpty();
    }

    @Test
    @DisplayName("공연 팀이면 같은 공연의 다른 팀이 고른 곡에 selectedByOtherTeam=true, 우리 팀이 고른 곡은 false")
    void performanceTeam_marksSongsSelectedByOtherTeams() {
        givenTeam(new PerformanceBriefResponse(PERFORMANCE_ID, "정기 공연", "밴드 동아리", LocalDateTime.now()));
        givenAi(List.of(item("ext-1", 0.9), item("ext-2", 0.8)));
        given(songService.findByExternalIds(anyCollection())).willReturn(Map.of(
                "ext-1", song(101L, Genre.ROCK, List.of()),
                "ext-2", song(102L, Genre.ROCK, List.of())));
        given(teamService.getActiveSummaries(PERFORMANCE_ID)).willReturn(List.of(
                new TeamSummaryResponse(TEAM_ID, "무명밴드", Region.SEOUL, List.of(), TeamStatus.ACTIVE, 3),
                new TeamSummaryResponse(OTHER_TEAM_ID, "옆밴드", Region.SEOUL, List.of(), TeamStatus.ACTIVE, 4)));
        given(setlistService.findTeamSongs(OTHER_TEAM_ID)).willReturn(List.of(
                new TeamSongResponse(1L, new SongSummaryResponse(102L, "곡102", "가수102"))));

        TeamSongRecommendationResponse response = service.recommendForTeam(LEADER_ID, TEAM_ID);

        assertThat(response.songs()).extracting(RecommendedSong::selectedByOtherTeam).containsExactly(false, true);
        verify(setlistService, never()).findTeamSongs(TEAM_ID);
    }

    @Test
    @DisplayName("독립 팀이면 다른 팀을 찾지 않고 selectedByOtherTeam은 항상 false다")
    void independentTeam_neverSelectedByOtherTeam() {
        givenTeam(null);
        givenAi(List.of(item("ext-1", 0.9)));
        given(songService.findByExternalIds(anyCollection()))
                .willReturn(Map.of("ext-1", song(101L, Genre.ROCK, List.of())));

        TeamSongRecommendationResponse response = service.recommendForTeam(LEADER_ID, TEAM_ID);

        assertThat(response.songs()).singleElement().extracting(RecommendedSong::selectedByOtherTeam).isEqualTo(false);
        verify(teamService, never()).getActiveSummaries(any());
    }

    // ---------- AI 실패 ----------

    @Test
    @DisplayName("AI 주소가 설정되지 않았으면 부르지 않고 503 SONG_RECOMMENDATION_UNAVAILABLE이다")
    void notConfigured_unavailable() {
        givenTeam(null);
        given(aiSongRecommendationClient.isConfigured()).willReturn(false);

        assertThatThrownBy(() -> service.recommendForTeam(LEADER_ID, TEAM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SONG_RECOMMENDATION_UNAVAILABLE);
        verify(aiSongRecommendationClient, never()).recommend(any());
    }

    @Test
    @DisplayName("AI 호출 실패(연결·시간 초과)나 응답 계약 위반이면 503 SONG_RECOMMENDATION_UNAVAILABLE이다")
    void aiFailure_unavailable() {
        givenTeam(null);
        given(aiSongRecommendationClient.recommend(any()))
                .willThrow(new ResourceAccessException("timeout"))
                .willThrow(new IllegalStateException("no songs"));

        assertThatThrownBy(() -> service.recommendForTeam(LEADER_ID, TEAM_ID))
                .extracting("errorCode").isEqualTo(ErrorCode.SONG_RECOMMENDATION_UNAVAILABLE);
        assertThatThrownBy(() -> service.recommendForTeam(LEADER_ID, TEAM_ID))
                .extracting("errorCode").isEqualTo(ErrorCode.SONG_RECOMMENDATION_UNAVAILABLE);
    }

    // ---------- 내 취향 추천곡 (이슈 #172) ----------

    private static UserMeResponse me(boolean onboardingCompleted, List<Genre> genres,
            List<UserInstrumentResponse> instruments) {
        return new UserMeResponse(LEADER_ID, "me@moyeorock.test", "나", Region.SEOUL, genres, null, null,
                PlatformRole.USER, LoginType.EMAIL, instruments, true, true, onboardingCompleted, LocalDateTime.now());
    }

    @Test
    @DisplayName("개인 추천: 온보딩을 마치지 않았으면 AI를 부르지 않고 빈 목록이다")
    void personal_notOnboarded_returnsEmptyWithoutAi() {
        given(userService.getMe(LEADER_ID)).willReturn(me(false, null, List.of()));

        SongRecommendationResponse response = service.recommendForUser(LEADER_ID);

        assertThat(response.songs()).isEmpty();
        verify(aiSongRecommendationClient, never()).recommend(any());
        verify(songService, never()).findByExternalIds(anyCollection());
    }

    @Test
    @DisplayName("개인 추천: AI에는 PERSONAL 모드로 내 선호 장르와 악기별 실력을 보내고 limit은 10이다")
    void personal_sendsGenresAndLevels() {
        given(userService.getMe(LEADER_ID)).willReturn(me(true, List.of(Genre.ROCK, Genre.INDIE), List.of(
                new UserInstrumentResponse(11L, Instrument.BASS, Level.NOVICE),
                new UserInstrumentResponse(12L, Instrument.VOCAL, Level.ADVANCED))));
        given(aiSongRecommendationClient.isConfigured()).willReturn(true);
        givenAi(List.of());
        given(songService.findByExternalIds(anyCollection())).willReturn(Map.of());

        service.recommendForUser(LEADER_ID);

        ArgumentCaptor<AiSongRecommendRequest> captor = ArgumentCaptor.forClass(AiSongRecommendRequest.class);
        verify(aiSongRecommendationClient).recommend(captor.capture());
        AiSongRecommendRequest sent = captor.getValue();
        assertThat(sent.mode()).isEqualTo("PERSONAL");
        assertThat(sent.genres()).containsExactly(Genre.ROCK, Genre.INDIE);
        assertThat(sent.members()).containsExactly(
                new AiSongRecommendRequest.Member(Instrument.BASS, Level.NOVICE),
                new AiSongRecommendRequest.Member(Instrument.VOCAL, Level.ADVANCED));
        assertThat(sent.limit()).isEqualTo(SongRecommendationService.PERSONAL_AI_REQUEST_LIMIT);
    }

    @Test
    @DisplayName("개인 추천: 선호 장르가 비어 있으면 빈 장르로 보낸다(온보딩은 마침)")
    void personal_nullGenres_sendsEmptyGenres() {
        given(userService.getMe(LEADER_ID)).willReturn(me(true, null, List.of()));
        given(aiSongRecommendationClient.isConfigured()).willReturn(true);
        givenAi(List.of());
        given(songService.findByExternalIds(anyCollection())).willReturn(Map.of());

        service.recommendForUser(LEADER_ID);

        ArgumentCaptor<AiSongRecommendRequest> captor = ArgumentCaptor.forClass(AiSongRecommendRequest.class);
        verify(aiSongRecommendationClient).recommend(captor.capture());
        assertThat(captor.getValue().genres()).isEmpty();
        assertThat(captor.getValue().members()).isEmpty();
    }

    @Test
    @DisplayName("개인 추천: 우리 DB에 없는 곡은 버리고 AI 순서대로 최대 2곡만 돌려준다")
    void personal_resolvesTopTwo() {
        given(userService.getMe(LEADER_ID)).willReturn(me(true, List.of(Genre.ROCK), List.of()));
        given(aiSongRecommendationClient.isConfigured()).willReturn(true);
        givenAi(List.of(item("ext-missing", 0.95), item("ext-1", 0.9), item("ext-2", 0.8), item("ext-3", 0.7)));
        given(songService.findByExternalIds(anyCollection())).willReturn(Map.of(
                "ext-1", song(101L, Genre.ROCK, List.of()),
                "ext-2", song(102L, Genre.ROCK, List.of()),
                "ext-3", song(103L, Genre.ROCK, List.of())));

        SongRecommendationResponse response = service.recommendForUser(LEADER_ID);

        assertThat(response.songs()).hasSize(SongRecommendationService.PERSONAL_RESULT_LIMIT);
        assertThat(response.songs()).extracting(r -> r.song().id()).containsExactly(101L, 102L);
        assertThat(response.songs()).extracting(SongRecommendationResponse.RecommendedSong::score)
                .containsExactly(0.9, 0.8);
    }

    @Test
    @DisplayName("개인 추천: AI 호출이 실패하면 503 SONG_RECOMMENDATION_UNAVAILABLE이다")
    void personal_aiFailure_unavailable() {
        given(userService.getMe(LEADER_ID)).willReturn(me(true, List.of(Genre.ROCK), List.of()));
        given(aiSongRecommendationClient.isConfigured()).willReturn(true);
        given(aiSongRecommendationClient.recommend(any())).willThrow(new ResourceAccessException("timeout"));

        assertThatThrownBy(() -> service.recommendForUser(LEADER_ID))
                .extracting("errorCode").isEqualTo(ErrorCode.SONG_RECOMMENDATION_UNAVAILABLE);
    }

    @Test
    @DisplayName("개인 추천: 없는 사용자면 UserService가 던진 404를 그대로 낸다")
    void personal_missingUser_notFound() {
        given(userService.getMe(LEADER_ID)).willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        assertThatThrownBy(() -> service.recommendForUser(LEADER_ID))
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
        verify(aiSongRecommendationClient, never()).recommend(any());
    }
}
