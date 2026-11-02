package com.moyeorock.domain.setlist.repository;

import com.moyeorock.domain.setlist.entity.Setlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SetlistRepository extends JpaRepository<Setlist, Long> {

    // 팀 개별 해체 때 그 팀이 넘긴 셋리스트 행 삭제(architecture.md §6 예외). 벌크 삭제 이유는 TeamSongRepository와 같다
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Setlist s WHERE s.teamId = :teamId")
    void deleteByTeamId(@Param("teamId") Long teamId);
}
