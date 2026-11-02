package com.moyeorock.domain.setlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moyeorock.domain.setlist.dto.request.TeamSongsSaveRequest;
import com.moyeorock.domain.setlist.dto.response.TeamSongResponse;
import com.moyeorock.domain.setlist.dto.response.TeamSongsResponse;
import com.moyeorock.domain.setlist.entity.TeamSong;
import com.moyeorock.domain.setlist.repository.SetlistRepository;
import com.moyeorock.domain.setlist.repository.TeamSongRepository;
import com.moyeorock.domain.song.dto.response.SongSummaryResponse;
import com.moyeorock.domain.song.service.SongService;
import com.moyeorock.domain.team.service.TeamMemberService;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SetlistServiceTest {

    private static final Long TEAM_ID = 5L;
    private static final Long LEADER_ID = 10L;
    private static final Long PERFORMANCE_ID = 7L;
    private static final SongSummaryResponse BOHEMIAN = new SongSummaryResponse(88L, "Bohemian Rhapsody", "Queen");
    private static final SongSummaryResponse CREEP = new SongSummaryResponse(104L, "Creep", "Radiohead");

    @Mock
    private TeamSongRepository teamSongRepository;

    @Mock
    private SetlistRepository setlistRepository;

    @Mock
    private SongService songService;

    @Mock
    private TeamMemberService teamMemberService;

    @InjectMocks
    private SetlistService setlistService;

    @Test
    @DisplayName("팀장이 선택곡을 저장하면 기존 선택곡을 지우고 요청 순서대로 팀의 공연 id와 함께 저장한다")
    void saveTeamSongs_replacesSongsInRequestOrder() {
        when(songService.getSummaries(List.of(104L, 88L))).thenReturn(Map.of(88L, BOHEMIAN, 104L, CREEP));
        when(teamMemberService.getPerformanceId(TEAM_ID)).thenReturn(PERFORMANCE_ID);
        when(teamSongRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        TeamSongsResponse response = setlistService.saveTeamSongs(LEADER_ID, TEAM_ID,
                new TeamSongsSaveRequest(List.of(104L, 88L)));

        InOrder order = inOrder(teamMemberService, teamSongRepository);
        order.verify(teamMemberService).ensureLeader(TEAM_ID, LEADER_ID);
        order.verify(teamSongRepository).deleteByTeamId(TEAM_ID);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TeamSong>> captor = ArgumentCaptor.forClass(List.class);
        order.verify(teamSongRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(TeamSong::getSongId).containsExactly(104L, 88L);
        assertThat(captor.getValue()).extracting(TeamSong::getPerformanceId).containsOnly(PERFORMANCE_ID);
        assertThat(response.teamId()).isEqualTo(TEAM_ID);
        assertThat(response.songs()).extracting(TeamSongResponse::song).containsExactly(CREEP, BOHEMIAN);
    }

    @Test
    @DisplayName("빈 목록이면 선택곡을 모두 지운다")
    void saveTeamSongs_withEmptyList_deletesAll() {
        when(songService.getSummaries(List.of())).thenReturn(Map.of());
        when(teamSongRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        TeamSongsResponse response = setlistService.saveTeamSongs(LEADER_ID, TEAM_ID, new TeamSongsSaveRequest(List.of()));

        verify(teamSongRepository).deleteByTeamId(TEAM_ID);
        assertThat(response.songs()).isEmpty();
    }

    @Test
    @DisplayName("팀장이 아니면 선택곡을 저장할 수 없고 아무것도 지우지 않는다")
    void saveTeamSongs_throws_whenNotLeader() {
        doThrow(new BusinessException(ErrorCode.NOT_TEAM_LEADER)).when(teamMemberService).ensureLeader(TEAM_ID, 20L);

        assertThatThrownBy(() -> setlistService.saveTeamSongs(20L, TEAM_ID, new TeamSongsSaveRequest(List.of(88L))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_TEAM_LEADER);
        verifyNoInteractions(teamSongRepository, songService);
    }

    @Test
    @DisplayName("같은 곡이 두 번 있으면 VALIDATION_FAILED이고 아무것도 지우지 않는다")
    void saveTeamSongs_throws_whenDuplicateSongs() {
        assertThatThrownBy(() -> setlistService.saveTeamSongs(LEADER_ID, TEAM_ID,
                new TeamSongsSaveRequest(List.of(88L, 88L))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(teamSongRepository);
    }

    @Test
    @DisplayName("없는 곡이 섞이면 SONG_NOT_FOUND이고 아무것도 지우지 않는다")
    void saveTeamSongs_throws_whenSongMissing() {
        when(songService.getSummaries(List.of(88L, 999L))).thenReturn(Map.of(88L, BOHEMIAN));

        assertThatThrownBy(() -> setlistService.saveTeamSongs(LEADER_ID, TEAM_ID,
                new TeamSongsSaveRequest(List.of(88L, 999L))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SONG_NOT_FOUND);
        verify(teamSongRepository, never()).deleteByTeamId(TEAM_ID);
    }

    @Test
    @DisplayName("팀원이 아니면 선택곡을 볼 수 없다")
    void getTeamSongs_throws_whenNotActiveMember() {
        doThrow(new BusinessException(ErrorCode.NO_PERMISSION)).when(teamMemberService).ensureActiveMember(TEAM_ID, 30L);

        assertThatThrownBy(() -> setlistService.getTeamSongs(30L, TEAM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NO_PERMISSION);
        verifyNoInteractions(teamSongRepository);
    }

    @Test
    @DisplayName("팀원은 저장한 순서대로 선택곡을 본다")
    void getTeamSongs_returnsSongsInSavedOrder() {
        when(teamSongRepository.findAllByTeamIdOrderByIdAsc(TEAM_ID))
                .thenReturn(List.of(TeamSong.create(TEAM_ID, null, 104L), TeamSong.create(TEAM_ID, null, 88L)));
        when(songService.getSummaries(List.of(104L, 88L))).thenReturn(Map.of(88L, BOHEMIAN, 104L, CREEP));

        TeamSongsResponse response = setlistService.getTeamSongs(LEADER_ID, TEAM_ID);

        verify(teamMemberService).ensureActiveMember(TEAM_ID, LEADER_ID);
        assertThat(response.songs()).extracting(TeamSongResponse::song).containsExactly(CREEP, BOHEMIAN);
    }

    @Test
    @DisplayName("팀 개별 해체 때는 그 팀이 넘긴 셋리스트와 선택곡을 모두 지운다")
    void deleteByTeam_deletesSetlistsAndTeamSongs() {
        setlistService.deleteByTeam(TEAM_ID);

        verify(setlistRepository).deleteByTeamId(TEAM_ID);
        verify(teamSongRepository).deleteByTeamId(TEAM_ID);
    }
}
