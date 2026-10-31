package com.moyeorock.domain.team.repository;

import com.moyeorock.domain.team.entity.TeamGenre;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamGenreRepository extends JpaRepository<TeamGenre, Long> {

    /** 여러 팀의 장르를 한 번에 가져온다. 팀마다 따로 조회하면 N+1이 된다. */
    List<TeamGenre> findAllByTeamIdIn(Collection<Long> teamIds);

    /**
     * 팀 정보 수정(전체 교체)에서 기존 장르를 지우는 데 쓴다.
     *
     * <p>파생 delete(deleteAllByTeamId)가 아니라 벌크 쿼리로 둔 이유: team_genres에는 (team_id, genre) 유니크가 있는데,
     * Hibernate는 한 트랜잭션 안에서 INSERT를 DELETE보다 먼저 실행한다. 같은 장르를 지웠다가 다시 넣으면 유니크 위반이 난다.
     * 벌크 쿼리는 호출 즉시 실행되므로 그 뒤의 INSERT가 안전하다. flushAutomatically로 쿼리 전에 밀린 변경을 먼저 반영한다.
     * clearAutomatically는 쓰지 않는다 — 영속성 컨텍스트를 비우면 같은 트랜잭션에서 방금 수정한 Team이 분리되어 저장이 빠진다.
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM TeamGenre g WHERE g.teamId = :teamId")
    void deleteByTeamId(@Param("teamId") Long teamId);
}
