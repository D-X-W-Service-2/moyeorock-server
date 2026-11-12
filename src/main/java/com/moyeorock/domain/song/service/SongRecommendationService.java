package com.moyeorock.domain.song.service;

import com.moyeorock.domain.song.client.AiRecommendationClient;
import com.moyeorock.domain.song.client.AiRecommendationClient.MemberLevel;
import com.moyeorock.domain.song.client.AiRecommendationClient.RecommendedSong;
import com.moyeorock.domain.song.dto.response.SongRecommendationResponse;
import com.moyeorock.domain.song.dto.response.SongResponse;
import com.moyeorock.domain.song.dto.response.TeamSongRecommendationResponse;
import com.moyeorock.domain.song.dto.response.TeamSongRecommendationResponse.RecommendedTeamSong;
import com.moyeorock.domain.song.entity.Song;
import com.moyeorock.domain.song.repository.SongRepository;
import com.moyeorock.domain.song.service.RecommendationEventService.ServedSong;
import com.moyeorock.domain.setlist.service.SetlistService;
import com.moyeorock.domain.team.dto.response.TeamRecommendationProfile;
import com.moyeorock.domain.team.dto.response.TeamRecommendationProfile.MemberPart;
import com.moyeorock.domain.team.service.TeamMemberService;
import com.moyeorock.domain.user.dto.response.UserInstrumentResponse;
import com.moyeorock.domain.user.dto.response.UserMeResponse;
import com.moyeorock.domain.user.service.UserService;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** The AI service selects songs from its catalog; the backend resolves them to DB IDs. */
@Service
@RequiredArgsConstructor
public class SongRecommendationService {

    private static final int RESULT_LIMIT = 20;

    private final SongRepository songRepository;
    private final UserService userService;
    private final TeamMemberService teamMemberService;
    private final SetlistService setlistService;
    private final AiRecommendationClient aiRecommendationClient;
    private final RecommendationEventService recommendationEventService;

    public SongRecommendationResponse recommend(Long userId) {
        UserMeResponse user = userService.getMe(userId);
        Map<Instrument, Level> levels = user.instruments().stream()
                .collect(Collectors.toMap(UserInstrumentResponse::instrument, UserInstrumentResponse::level));
        List<Genre> genres = user.genres() == null ? List.of() : user.genres();

        List<MemberLevel> members = levels.entrySet().stream()
                .map(entry -> new MemberLevel(entry.getKey(), entry.getValue())).toList();
        var recommendation = recommendFromAi("PERSONAL", genres, members);
        Map<String, Song> songsByExternalId = songsByExternalId(recommendation.songs());
        List<SongRecommendationResponse.RecommendedSong> songs = recommendation.songs().stream()
                .map(item -> new SongRecommendationResponse.RecommendedSong(
                        SongResponse.from(songsByExternalId.get(item.externalId())), item.score()))
                .toList();
        String requestId = recommendationEventService.recordServed(userId, null, "PERSONAL",
                recommendation.ranker(),
                IntStream.range(0, songs.size()).mapToObj(index ->
                        new ServedSong(songs.get(index).song().id(), index + 1, songs.get(index).score())).toList());
        return new SongRecommendationResponse(requestId, songs);
    }

    public TeamSongRecommendationResponse recommendTeam(Long userId, Long teamId) {
        TeamRecommendationProfile profile = teamMemberService.getRecommendationProfile(teamId, userId);
        List<Long> memberIds = profile.members().stream().map(MemberPart::userId).distinct().toList();
        Map<Long, List<UserInstrumentResponse>> instruments = userService.getInstruments(memberIds);
        List<MemberLevel> memberLevels = profile.members().stream()
                .flatMap(member -> instruments.getOrDefault(member.userId(), List.of()).stream()
                        .filter(item -> item.instrument() == member.instrument())
                        .map(item -> new MemberLevel(member.instrument(), item.level())))
                .toList();
        var recommendation = recommendFromAi("TEAM", profile.genres(), memberLevels);
        Map<String, Song> songsByExternalId = songsByExternalId(recommendation.songs());
        Set<Long> selectedElsewhere = setlistService.getSongIdsSelectedByOtherTeam(profile.performanceId(), teamId);
        List<RecommendedTeamSong> songs = recommendation.songs().stream()
                .map(item -> {
                    Song song = songsByExternalId.get(item.externalId());
                    return teamResult(song, profile.genres(), memberLevels,
                            selectedElsewhere.contains(song.getId()), item.score());
                })
                .toList();
        String requestId = recommendationEventService.recordServed(userId, teamId, "TEAM",
                recommendation.ranker(),
                IntStream.range(0, songs.size()).mapToObj(index ->
                        new ServedSong(songs.get(index).song().id(), index + 1, songs.get(index).score())).toList());
        return new TeamSongRecommendationResponse(teamId, requestId, songs);
    }

    private AiRecommendationClient.RecommendResult recommendFromAi(String mode, List<Genre> genres,
                                                                    List<MemberLevel> members) {
        try {
            return aiRecommendationClient.recommend(mode, genres, members, RESULT_LIMIT);
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.RECOMMENDATION_UNAVAILABLE);
        }
    }

    private Map<String, Song> songsByExternalId(List<RecommendedSong> recommendations) {
        List<String> externalIds = recommendations.stream().map(RecommendedSong::externalId).toList();
        if (externalIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Song> songs = songRepository.findAllByExternalIdIn(externalIds).stream()
                .collect(Collectors.toMap(Song::getExternalId, song -> song));
        if (songs.size() != externalIds.size()) {
            throw new BusinessException(ErrorCode.RECOMMENDATION_UNAVAILABLE);
        }
        return songs;
    }

    private RecommendedTeamSong teamResult(Song song, List<Genre> genres, List<MemberLevel> members,
            boolean selectedElsewhere, double aiScore) {
        List<String> reasons = new ArrayList<>();
        if (song.getGenre() != null && genres.contains(song.getGenre())) {
            reasons.add("팀 장르 일치: " + song.getGenre());
        }
        int count = 0;
        Map<Instrument, Level> difficulty = song.getDifficulty();
        if (difficulty != null) {
            for (MemberLevel member : members) {
                Level required = difficulty.get(member.part());
                if (required != null) {
                    count++;
                    reasons.add(member.part() + (required.ordinal() <= member.level().ordinal()
                            ? " 실력 충족" : " 난이도 초과"));
                }
            }
        }
        if (count == 0) {
            reasons.add("파트 난이도 정보 없음");
        }
        if (selectedElsewhere) {
            reasons.add("같은 공연의 다른 팀이 선택함");
        }
        return new RecommendedTeamSong(SongResponse.from(song),
                aiScore,
                List.copyOf(reasons), selectedElsewhere);
    }
}
