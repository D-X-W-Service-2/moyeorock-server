package com.moyeorock.domain.setlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moyeorock.domain.setlist.dto.request.TeamSongsSaveRequest;
import com.moyeorock.domain.setlist.dto.response.SetlistItemResponse;
import com.moyeorock.domain.setlist.dto.response.TeamSongResponse;
import com.moyeorock.domain.setlist.dto.response.TeamSongsResponse;
import com.moyeorock.domain.setlist.entity.Setlist;
import com.moyeorock.domain.setlist.entity.TeamSong;
import com.moyeorock.domain.setlist.repository.SetlistRepository;
import com.moyeorock.domain.setlist.repository.TeamSongRepository;
import com.moyeorock.domain.song.dto.response.SongSummaryResponse;
import com.moyeorock.domain.song.service.SongService;
import com.moyeorock.domain.team.service.TeamMemberService;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.util.Arrays;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

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
    @DisplayName("확정 전 셋리스트는 팀별·넘긴 순 조회를 쓰고, 해체된 팀 이름·곡 정보를 붙인다")
    void getSetlist_unconfirmed_usesTeamOrder_andAttachesNames() {
        when(setlistRepository.findAllByPerformanceIdOrderByTeamIdAscIdAsc(PERFORMANCE_ID))
                .thenReturn(List.of(Setlist.create(PERFORMANCE_ID, TEAM_ID, 88L), Setlist.create(PERFORMANCE_ID, 9L, 104L)));
        when(teamMemberService.getNames(List.of(TEAM_ID, 9L))).thenReturn(Map.of(TEAM_ID, "무명밴드", 9L, "해체된 팀"));
        when(songService.getSummaries(List.of(88L, 104L))).thenReturn(Map.of(88L, BOHEMIAN, 104L, CREEP));

        List<SetlistItemResponse> setlist = setlistService.getSetlist(PERFORMANCE_ID, false);

        assertThat(setlist).containsExactly(
                new SetlistItemResponse(TEAM_ID, "무명밴드", BOHEMIAN, null),
                new SetlistItemResponse(9L, "해체된 팀", CREEP, null));
    }

    @Test
    @DisplayName("확정 후 셋리스트는 모임장이 정한 순서 조회를 쓴다")
    void getSetlist_confirmed_usesSortOrder() {
        Setlist confirmed = Setlist.create(PERFORMANCE_ID, TEAM_ID, 88L);
        ReflectionTestUtils.setField(confirmed, "sortOrder", 1);
        when(setlistRepository.findAllByPerformanceIdOrderBySortOrderAscIdAsc(PERFORMANCE_ID)).thenReturn(List.of(confirmed));
        when(teamMemberService.getNames(List.of(TEAM_ID))).thenReturn(Map.of(TEAM_ID, "무명밴드"));
        when(songService.getSummaries(List.of(88L))).thenReturn(Map.of(88L, BOHEMIAN));

        assertThat(setlistService.getSetlist(PERFORMANCE_ID, true))
                .containsExactly(new SetlistItemResponse(TEAM_ID, "무명밴드", BOHEMIAN, 1));
    }

    @Test
    @DisplayName("넘기기는 그 팀이 넘긴 곡을 지우고 요청 순서대로 다시 넣는다")
    void submit_replacesTeamRowsInRequestOrder() {
        givenSelected(88L, 104L);
        when(songService.getSummaries(List.of(104L, 88L))).thenReturn(Map.of(88L, BOHEMIAN, 104L, CREEP));
        when(setlistRepository.existsByPerformanceIdAndTeamIdNotAndSongIdIn(PERFORMANCE_ID, TEAM_ID, List.of(104L, 88L)))
                .thenReturn(false);

        setlistService.submit(PERFORMANCE_ID, TEAM_ID, List.of(104L, 88L));

        InOrder order = inOrder(setlistRepository);
        order.verify(setlistRepository).deleteByPerformanceIdAndTeamId(PERFORMANCE_ID, TEAM_ID);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Setlist>> captor = ArgumentCaptor.forClass(List.class);
        order.verify(setlistRepository).saveAllAndFlush(captor.capture());
        assertThat(captor.getValue()).extracting(Setlist::getSongId).containsExactly(104L, 88L);
        assertThat(captor.getValue()).extracting(Setlist::getTeamId).containsOnly(TEAM_ID);
    }

    @Test
    @DisplayName("같은 곡이 두 번이면 VALIDATION_FAILED, 없는 곡이면 SONG_NOT_FOUND이고 아무것도 지우지 않는다")
    void submit_throws_whenDuplicateOrMissingSong() {
        assertThatThrownBy(() -> setlistService.submit(PERFORMANCE_ID, TEAM_ID, List.of(88L, 88L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);

        when(songService.getSummaries(List.of(88L, 999L))).thenReturn(Map.of(88L, BOHEMIAN));
        assertThatThrownBy(() -> setlistService.submit(PERFORMANCE_ID, TEAM_ID, List.of(88L, 999L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SONG_NOT_FOUND);
        verify(setlistRepository, never()).deleteByPerformanceIdAndTeamId(PERFORMANCE_ID, TEAM_ID);
    }

    @Test
    @DisplayName("팀 선택곡에 없는 곡을 넘기면 SONG_NOT_SELECTED_BY_TEAM이다")
    void submit_throws_whenSongNotSelectedByTeam() {
        givenSelected(88L);
        when(songService.getSummaries(List.of(104L))).thenReturn(Map.of(104L, CREEP));

        assertThatThrownBy(() -> setlistService.submit(PERFORMANCE_ID, TEAM_ID, List.of(104L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SONG_NOT_SELECTED_BY_TEAM);
        verify(setlistRepository, never()).deleteByPerformanceIdAndTeamId(PERFORMANCE_ID, TEAM_ID);
    }

    @Test
    @DisplayName("같은 공연의 다른 팀이 이미 넘긴 곡이면 SONG_ALREADY_IN_SETLIST이다")
    void submit_throws_whenOtherTeamAlreadySubmitted() {
        givenSelected(88L);
        when(songService.getSummaries(List.of(88L))).thenReturn(Map.of(88L, BOHEMIAN));
        when(setlistRepository.existsByPerformanceIdAndTeamIdNotAndSongIdIn(PERFORMANCE_ID, TEAM_ID, List.of(88L)))
                .thenReturn(true);

        assertThatThrownBy(() -> setlistService.submit(PERFORMANCE_ID, TEAM_ID, List.of(88L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SONG_ALREADY_IN_SETLIST);
    }

    @Test
    @DisplayName("사전 검사를 통과했어도 동시에 다른 팀이 먼저 넣어 DB 유니크에 걸리면 SONG_ALREADY_IN_SETLIST로 바꾼다")
    void submit_translatesUniqueViolation() {
        givenSelected(88L);
        when(songService.getSummaries(List.of(88L))).thenReturn(Map.of(88L, BOHEMIAN));
        when(setlistRepository.existsByPerformanceIdAndTeamIdNotAndSongIdIn(PERFORMANCE_ID, TEAM_ID, List.of(88L)))
                .thenReturn(false);
        when(setlistRepository.saveAllAndFlush(anyList())).thenThrow(new DataIntegrityViolationException("uk"));

        assertThatThrownBy(() -> setlistService.submit(PERFORMANCE_ID, TEAM_ID, List.of(88L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SONG_ALREADY_IN_SETLIST);
    }

    @Test
    @DisplayName("팀 개별 해체 때는 그 팀이 넘긴 셋리스트와 선택곡을 모두 지운다")
    void deleteByTeam_deletesSetlistsAndTeamSongs() {
        setlistService.deleteByTeam(TEAM_ID);

        verify(setlistRepository).deleteByTeamId(TEAM_ID);
        verify(teamSongRepository).deleteByTeamId(TEAM_ID);
    }

    private void givenSelected(Long... songIds) {
        when(teamSongRepository.findAllByTeamIdOrderByIdAsc(eq(TEAM_ID)))
                .thenReturn(Arrays.stream(songIds).map(id -> TeamSong.create(TEAM_ID, PERFORMANCE_ID, id)).toList());
    }
}
