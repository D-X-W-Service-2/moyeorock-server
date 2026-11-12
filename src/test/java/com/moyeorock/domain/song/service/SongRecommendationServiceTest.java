package com.moyeorock.domain.song.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moyeorock.domain.setlist.service.SetlistService;
import com.moyeorock.domain.song.client.AiRecommendationClient;
import com.moyeorock.domain.song.client.AiRecommendationClient.RecommendResult;
import com.moyeorock.domain.song.client.AiRecommendationClient.RecommendedSong;
import com.moyeorock.domain.song.entity.Song;
import com.moyeorock.domain.song.repository.SongRepository;
import com.moyeorock.domain.team.dto.response.TeamRecommendationProfile;
import com.moyeorock.domain.team.dto.response.TeamRecommendationProfile.MemberPart;
import com.moyeorock.domain.team.service.TeamMemberService;
import com.moyeorock.domain.user.dto.response.UserInstrumentResponse;
import com.moyeorock.domain.user.dto.response.UserMeResponse;
import com.moyeorock.domain.user.service.UserService;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SongRecommendationServiceTest {

    @Mock private SongRepository songRepository;
    @Mock private UserService userService;
    @Mock private TeamMemberService teamMemberService;
    @Mock private SetlistService setlistService;
    @Mock private AiRecommendationClient aiRecommendationClient;
    @Mock private RecommendationEventService recommendationEventService;
    @InjectMocks private SongRecommendationService recommendationService;

    @Test
    void sendsProfileOnlyAndPreservesAiRanking() {
        Song first = song(1L, "first", Genre.ROCK, null);
        Song second = song(2L, "second", Genre.POP, null);
        when(userService.getMe(7L)).thenReturn(user());
        when(aiRecommendationClient.recommend(eq("PERSONAL"), eq(List.of(Genre.ROCK)),
                eq(List.of(new AiRecommendationClient.MemberLevel(Instrument.BASS, Level.NOVICE))), eq(20)))
                .thenReturn(new RecommendResult(List.of(new RecommendedSong("second", 0.9),
                        new RecommendedSong("first", 0.2)), "content_rule"));
        when(songRepository.findAllByExternalIdIn(List.of("second", "first")))
                .thenReturn(List.of(first, second));
        when(recommendationEventService.recordServed(eq(7L), eq(null), eq("PERSONAL"),
                eq("content_rule"), anyList())).thenReturn("request-123");

        var response = recommendationService.recommend(7L);

        assertThat(response.requestId()).isEqualTo("request-123");
        assertThat(response.songs()).extracting(item -> item.song().id()).containsExactly(2L, 1L);
        assertThat(response.songs()).extracting(item -> item.score()).containsExactly(0.9, 0.2);
        verify(recommendationEventService).recordServed(eq(7L), eq(null), eq("PERSONAL"),
                eq("content_rule"), org.mockito.ArgumentMatchers.argThat(items ->
                        items.size() == 2 && items.get(0).songId() == 2L && items.get(0).position() == 1));
    }

    @Test
    void teamRecommendationUsesTeamProfileAndAnnotatesOtherTeamSelection() {
        Song recommended = song(3L, "team-song", Genre.ROCK,
                Map.of(Instrument.BASS, Level.ADVANCED));
        when(teamMemberService.getRecommendationProfile(5L, 7L)).thenReturn(new TeamRecommendationProfile(10L,
                List.of(Genre.ROCK), List.of(new MemberPart(7L, Instrument.BASS))));
        when(userService.getInstruments(List.of(7L))).thenReturn(Map.of(7L,
                List.of(new UserInstrumentResponse(1L, Instrument.BASS, Level.NOVICE))));
        when(aiRecommendationClient.recommend(eq("TEAM"), eq(List.of(Genre.ROCK)), anyList(), eq(20)))
                .thenReturn(new RecommendResult(List.of(new RecommendedSong("team-song", 0.8)), "content_rule"));
        when(songRepository.findAllByExternalIdIn(List.of("team-song"))).thenReturn(List.of(recommended));
        when(setlistService.getSongIdsSelectedByOtherTeam(10L, 5L)).thenReturn(Set.of(3L));

        var response = recommendationService.recommendTeam(7L, 5L);

        assertThat(response.teamId()).isEqualTo(5L);
        assertThat(response.songs().get(0).score()).isEqualTo(0.8);
        assertThat(response.songs().get(0).selectedByOtherTeam()).isTrue();
        assertThat(response.songs().get(0).reasons()).contains("BASS 난이도 초과");
    }

    @Test
    void failsClearlyWhenAiSongIsMissingFromBackendCatalog() {
        when(userService.getMe(7L)).thenReturn(user());
        when(aiRecommendationClient.recommend(eq("PERSONAL"), anyList(), anyList(), eq(20)))
                .thenReturn(new RecommendResult(List.of(new RecommendedSong("not-synced", 0.9)), "content_rule"));
        when(songRepository.findAllByExternalIdIn(List.of("not-synced"))).thenReturn(List.of());

        assertThatThrownBy(() -> recommendationService.recommend(7L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.RECOMMENDATION_UNAVAILABLE));
    }

    private UserMeResponse user() {
        return new UserMeResponse(7L, null, "tester", null,
                List.of(Genre.ROCK), null, null, null, null,
                List.of(new UserInstrumentResponse(1L, Instrument.BASS, Level.NOVICE)),
                true, true, true, null);
    }

    private Song song(Long id, String externalId, Genre genre, Map<Instrument, Level> difficulty) {
        Song song = Song.create("title", "artist", genre, null, null, difficulty, externalId);
        ReflectionTestUtils.setField(song, "id", id);
        return song;
    }
}
