package com.moyeorock.domain.setlist.repository;

import com.moyeorock.domain.setlist.entity.TeamSong;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamSongRepository extends JpaRepository<TeamSong, Long> {

    // 저장한 순서(id 순)
    List<TeamSong> findAllByTeamIdOrderByIdAsc(Long teamId);

    @Query("SELECT DISTINCT t.songId FROM TeamSong t WHERE t.performanceId = :performanceId AND t.teamId <> :teamId")
    List<Long> findSongIdsSelectedByOtherTeam(@Param("performanceId") Long performanceId,
            @Param("teamId") Long teamId);

    /**
     * 그 팀의 선택곡 전부 삭제(전체 교체·팀 개별 해체). 벌크 삭제여야 한다 — Hibernate는 한 트랜잭션 안에서 INSERT를
     * DELETE보다 먼저 내보내서, 같은 곡을 지웠다가 다시 넣으면 UNIQUE(team_id, song_id)에 걸린다(TeamGenreRepository 선례).
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM TeamSong t WHERE t.teamId = :teamId")
    void deleteByTeamId(@Param("teamId") Long teamId);
}
