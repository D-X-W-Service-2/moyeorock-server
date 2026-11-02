package com.moyeorock.domain.setlist.service;

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팀 선택곡(team_songs)·공연 셋리스트(setlists) 행 처리. 공연 단위 셋리스트 엔드포인트는 performance가 받고
 * 공연 확인(존재·모임장·확정 여부)도 PerformanceService가 한다 — 여기서는 행만 다룬다(domains.md "헷갈리는 경계").
 *
 * <p>2팀 TeamService가 이 Service를 부르므로(팀 개별 해체 시 deleteByTeam, 팀 상세 songs) 여기서 TeamService·
 * PerformanceService를 부르면 순환한다. 팀 확인은 다른 Service를 부르지 않는 {@link TeamMemberService}로 한다.
 */
@Service
@RequiredArgsConstructor
public class SetlistService {

    private final TeamSongRepository teamSongRepository;
    private final SetlistRepository setlistRepository;
    private final SongService songService;
    private final TeamMemberService teamMemberService;

    /**
     * 팀 선택곡 저장 — 기존 선택곡 전체를 요청 목록으로 바꾼다. 팀장만 가능하고(해체·없는 팀은 TEAM_NOT_FOUND), 같은 곡 중복은
     * VALIDATION_FAILED, 없는 곡이 섞이면 SONG_NOT_FOUND다. 공연 팀이면 팀의 공연 id를 행에 복사해 둔다(TeamSong 주석).
     * 이미 셋리스트로 넘긴 곡은 바뀌지 않는다 — 셋리스트는 넘길 때 복사한 행이다.
     */
    @Transactional
    public TeamSongsResponse saveTeamSongs(Long userId, Long teamId, TeamSongsSaveRequest request) {
        teamMemberService.ensureLeader(teamId, userId);
        List<Long> songIds = request.songIds();
        if (new HashSet<>(songIds).size() != songIds.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        Map<Long, SongSummaryResponse> songs = songService.getSummaries(songIds);
        if (songs.size() != songIds.size()) {
            throw new BusinessException(ErrorCode.SONG_NOT_FOUND);
        }

        Long performanceId = teamMemberService.getPerformanceId(teamId);
        teamSongRepository.deleteByTeamId(teamId);
        List<TeamSong> saved = teamSongRepository.saveAll(
                songIds.stream().map(songId -> TeamSong.create(teamId, performanceId, songId)).toList());
        return new TeamSongsResponse(teamId, toResponses(saved, songs));
    }

    /** 팀 선택곡 조회(팀원). 해체·없는 팀은 TEAM_NOT_FOUND, 팀원이 아니면 NO_PERMISSION(TeamMemberService). */
    public TeamSongsResponse getTeamSongs(Long userId, Long teamId) {
        teamMemberService.ensureActiveMember(teamId, userId);
        return new TeamSongsResponse(teamId, getTeamSongs(teamId));
    }

    // ---------- 타 도메인용 ----------

    /** 팀 선택곡 목록(2팀 팀 상세의 songs). 권한은 묻지 않는다 — 부르는 쪽이 확인한다. 저장한 순서다. */
    public List<TeamSongResponse> getTeamSongs(Long teamId) {
        List<TeamSong> teamSongs = teamSongRepository.findAllByTeamIdOrderByIdAsc(teamId);
        Map<Long, SongSummaryResponse> songs = songService.getSummaries(
                teamSongs.stream().map(TeamSong::getSongId).toList());
        return toResponses(teamSongs, songs);
    }

    /**
     * 팀 개별 해체 때 그 팀이 넘긴 셋리스트와 선택곡을 지운다(2팀 TeamService가 해체와 같은 트랜잭션에서 부른다).
     * 공연 종료·취소로 인한 일괄 해체에서는 부르지 않는다 — 끝난 공연의 기록을 남긴다(architecture.md §6).
     */
    @Transactional
    public void deleteByTeam(Long teamId) {
        setlistRepository.deleteByTeamId(teamId);
        teamSongRepository.deleteByTeamId(teamId);
    }

    // 곡이 카탈로그에서 지워졌으면(지금은 지우는 경로가 없다) song이 null로 내려간다
    private List<TeamSongResponse> toResponses(List<TeamSong> teamSongs, Map<Long, SongSummaryResponse> songs) {
        return teamSongs.stream().map(t -> TeamSongResponse.of(t, songs.get(t.getSongId()))).toList();
    }
}
