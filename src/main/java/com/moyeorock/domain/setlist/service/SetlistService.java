package com.moyeorock.domain.setlist.service;

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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

    // ---------- 공연 셋리스트 (PerformanceService가 공연·팀 확인 뒤 부른다) ----------

    /**
     * 공연 셋리스트 행. 확정 전에는 팀들이 넘긴 곡 전체를 팀별·넘긴 순으로, 확정 후에는 확정곡을 순서대로 준다.
     * 팀 이름은 해체된 팀도 나온다(TeamMemberService.getNames) — 끝난 공연의 셋리스트를 남긴다.
     */
    public List<SetlistItemResponse> getSetlist(Long performanceId, boolean confirmed) {
        List<Setlist> rows = confirmed
                ? setlistRepository.findAllByPerformanceIdOrderBySortOrderAscIdAsc(performanceId)
                : setlistRepository.findAllByPerformanceIdOrderByTeamIdAscIdAsc(performanceId);
        Map<Long, String> teamNames = teamMemberService.getNames(rows.stream().map(Setlist::getTeamId).distinct().toList());
        Map<Long, SongSummaryResponse> songs = songService.getSummaries(rows.stream().map(Setlist::getSongId).toList());
        return rows.stream()
                .map(row -> SetlistItemResponse.of(row, teamNames.get(row.getTeamId()), songs.get(row.getSongId())))
                .toList();
    }

    /**
     * 셋리스트로 넘기기 — 그 팀이 그 공연에 넘긴 곡 전체를 요청 목록으로 바꾼다. 공연·팀장·확정 여부는 부르는 쪽이 확인한다.
     * 같은 곡 중복 VALIDATION_FAILED, 없는 곡 SONG_NOT_FOUND, 팀 선택곡에 없는 곡 SONG_NOT_SELECTED_BY_TEAM,
     * 같은 공연의 다른 팀이 이미 넘긴 곡 SONG_ALREADY_IN_SETLIST다. PerformanceService가 공연 행을 잠그고 불러 같은 공연의 넘기기는
     * 차례로 들어오므로 사전 검사가 앞 넘기기의 커밋을 본다. DB 유니크 위반도 같은 에러로 바꾸는 건 그 밖의 경우를 위한 안전망이다(이슈 #103).
     */
    @Transactional
    public void submit(Long performanceId, Long teamId, List<Long> songIds) {
        if (new HashSet<>(songIds).size() != songIds.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (songService.getSummaries(songIds).size() != songIds.size()) {
            throw new BusinessException(ErrorCode.SONG_NOT_FOUND);
        }
        Set<Long> selected = teamSongRepository.findAllByTeamIdOrderByIdAsc(teamId).stream()
                .map(TeamSong::getSongId)
                .collect(Collectors.toSet());
        if (!selected.containsAll(songIds)) {
            throw new BusinessException(ErrorCode.SONG_NOT_SELECTED_BY_TEAM);
        }
        if (!songIds.isEmpty()
                && setlistRepository.existsByPerformanceIdAndTeamIdNotAndSongIdIn(performanceId, teamId, songIds)) {
            throw new BusinessException(ErrorCode.SONG_ALREADY_IN_SETLIST);
        }

        setlistRepository.deleteByPerformanceIdAndTeamId(performanceId, teamId);
        try {
            setlistRepository.saveAllAndFlush(
                    songIds.stream().map(songId -> Setlist.create(performanceId, teamId, songId)).toList());
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.SONG_ALREADY_IN_SETLIST);
        }
    }

    /**
     * 셋리스트 확정 — 요청 항목만 남기고 순서를 매긴다. 요청에 없는 행은 지운다. 공연·모임장·팀 소속은 부르는 쪽이 확인한다.
     * 같은 곡 중복이나 순서가 1부터 빈틈없이 매겨지지 않았으면 VALIDATION_FAILED, 그 팀이 넘긴 곡이 아니면
     * SONG_NOT_TEAM_CANDIDATE다. 이미 확정된 셋리스트도 다시 확정할 수 있다(남아 있는 곡 안에서).
     */
    @Transactional
    public void confirm(Long performanceId, List<ConfirmItem> items) {
        Set<Long> songIds = items.stream().map(ConfirmItem::songId).collect(Collectors.toSet());
        Set<Integer> sortOrders = items.stream().map(ConfirmItem::sortOrder).collect(Collectors.toSet());
        Set<Integer> expected = IntStream.rangeClosed(1, items.size()).boxed().collect(Collectors.toSet());
        if (songIds.size() != items.size() || !sortOrders.equals(expected)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        Map<Long, Setlist> rows = setlistRepository.findAllByPerformanceId(performanceId).stream()
                .collect(Collectors.toMap(Setlist::getSongId, Function.identity()));
        for (ConfirmItem item : items) {
            Setlist row = rows.get(item.songId());
            if (row == null || !row.getTeamId().equals(item.teamId())) {
                throw new BusinessException(ErrorCode.SONG_NOT_TEAM_CANDIDATE);
            }
        }

        setlistRepository.deleteAll(rows.values().stream().filter(row -> !songIds.contains(row.getSongId())).toList());
        items.forEach(item -> rows.get(item.songId()).changeSortOrder(item.sortOrder()));
    }

    /** 확정 해제 — 행은 그대로 두고 순서만 비운다. 공연·모임장·확정 여부는 부르는 쪽이 확인한다. */
    @Transactional
    public void unconfirm(Long performanceId) {
        setlistRepository.findAllByPerformanceId(performanceId).forEach(Setlist::clearSortOrder);
    }

    // ---------- 타 도메인용 ----------

    /**
     * 셋리스트에 곡이 남아 있는 팀 id(팀 id 순). 해체된 팀도 들어간다 — 끝난 공연의 참여 팀을 이 기준으로 보여준다(#96).
     * 확정된 셋리스트라면 확정곡이 있는 팀만 남는다.
     */
    public List<Long> findTeamIds(Long performanceId) {
        return setlistRepository.findTeamIdsByPerformanceId(performanceId);
    }

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

    /**
     * 확정 항목 한 줄 — PerformanceService가 요청 DTO({@code SetlistConfirmRequest})를 이 값으로 바꿔 넘긴다.
     * 요청 DTO를 도메인 밖으로 넘기지 않기 위한 값이다(dto-naming.md §9).
     */
    public record ConfirmItem(Long teamId, Long songId, int sortOrder) {
    }
}
