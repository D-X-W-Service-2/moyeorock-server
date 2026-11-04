package com.moyeorock.domain.rehearsal.repository;

import com.moyeorock.domain.rehearsal.entity.Rehearsal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RehearsalRepository extends JpaRepository<Rehearsal, Long> {

    /**
     * 팀 하나의 기간 조회(팀 합주 목록). 시작 시각이 [periodStart, periodEnd) 안인 합주를 시작 시각 순으로 돌려준다.
     * 끝을 "미만"으로 둔 것은 Service가 to 날짜의 다음 날 0시를 넘기기 때문이다 — "이하"로 하면 그 0시 정각의 합주가 딸려 온다.
     * 시작이 같은 합주는 id 순으로 두어 호출마다 순서가 흔들리지 않게 한다.
     * 파라미터 이름에 from/to를 쓰지 않은 것은 JPQL 예약어와 겹치지 않으려는 것이다.
     */
    @Query("""
            SELECT r FROM Rehearsal r
            WHERE r.teamId = :teamId
              AND r.startsAt >= :periodStart
              AND r.startsAt < :periodEnd
            ORDER BY r.startsAt ASC, r.id ASC
            """)
    List<Rehearsal> findInPeriod(@Param("teamId") Long teamId,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd);

    /**
     * 여러 팀의 기간 조회(내 합주 일정). 조건과 정렬은 findInPeriod와 같고 팀 조건만 목록이다.
     * 해체된 팀을 거르는 일은 호출하는 쪽이 한다 — 활동 중인 팀 id만 넘기면 된다(architecture.md §6).
     */
    @Query("""
            SELECT r FROM Rehearsal r
            WHERE r.teamId IN :teamIds
              AND r.startsAt >= :periodStart
              AND r.startsAt < :periodEnd
            ORDER BY r.startsAt ASC, r.id ASC
            """)
    List<Rehearsal> findInPeriodByTeamIds(@Param("teamIds") Collection<Long> teamIds,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd);
}
