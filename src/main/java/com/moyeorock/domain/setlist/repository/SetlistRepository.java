package com.moyeorock.domain.setlist.repository;

import com.moyeorock.domain.setlist.entity.Setlist;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SetlistRepository extends JpaRepository<Setlist, Long> {

    // 확정 전 셋리스트 — 팀별(팀 id 순), 팀 안에서는 넘긴 순
    List<Setlist> findAllByPerformanceIdOrderByTeamIdAscIdAsc(Long performanceId);

    // 확정 후 셋리스트 — 모임장이 정한 순서
    List<Setlist> findAllByPerformanceIdOrderBySortOrderAscIdAsc(Long performanceId);

    // 같은 공연의 다른 팀이 이미 넘긴 곡이 있는지(넘기기 사전 검사 — 동시에 넘기면 UNIQUE(performance_id, song_id)가 막는다)
    boolean existsByPerformanceIdAndTeamIdNotAndSongIdIn(Long performanceId, Long teamId, Collection<Long> songIds);

    // 그 팀이 그 공연에 넘긴 곡 전부 삭제(넘기기는 전체 교체). 벌크 삭제 이유는 TeamSongRepository와 같다
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Setlist s WHERE s.performanceId = :performanceId AND s.teamId = :teamId")
    void deleteByPerformanceIdAndTeamId(@Param("performanceId") Long performanceId, @Param("teamId") Long teamId);

    // 팀 개별 해체 때 그 팀이 넘긴 셋리스트 행 삭제(architecture.md §6 예외). 벌크 삭제 이유는 TeamSongRepository와 같다
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Setlist s WHERE s.teamId = :teamId")
    void deleteByTeamId(@Param("teamId") Long teamId);
}
