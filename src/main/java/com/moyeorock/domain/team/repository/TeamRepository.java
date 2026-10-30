package com.moyeorock.domain.team.repository;

import com.moyeorock.domain.team.entity.Team;
import com.moyeorock.domain.team.enums.MemberStatus;
import com.moyeorock.domain.team.enums.TeamStatus;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Region;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamRepository extends JpaRepository<Team, Long> {

    /**
     * 팀 목록. region·genre는 선택 조건이라 null이면 걸지 않는다. status는 필수다 — 기본값(ACTIVE)은 Service가 정한다.
     * 정렬은 쿼리에 고정한다(최신순). 클라이언트의 sort 파라미터가 섞이면 ORDER BY가 중복되므로 호출 쪽은 정렬 없는 Pageable을 넘긴다.
     *
     * <p>연관관계 매핑이 없어서 장르는 team_genres를 JOIN하지 않고 EXISTS로 거른다. JOIN하면 장르가 여러 개인 팀이
     * 장르 수만큼 중복 행으로 나오기 때문이다.
     */
    @Query("""
            SELECT t FROM Team t
            WHERE t.status = :status
              AND (:region IS NULL OR t.region = :region)
              AND (:genre IS NULL
                   OR EXISTS (SELECT 1 FROM TeamGenre g WHERE g.teamId = t.id AND g.genre = :genre))
            ORDER BY t.createdAt DESC, t.id DESC
            """)
    Page<Team> search(@Param("region") Region region,
            @Param("genre") Genre genre,
            @Param("status") TeamStatus status,
            Pageable pageable);

    /**
     * 내 팀 목록. "내가 ACTIVE 팀원이고 팀도 ACTIVE"라는 규칙(architecture.md §6)을 한 곳에 고정하려고
     * 상태 값을 파라미터로 받는 쿼리를 감싸 두었다 — 호출하는 쪽이 상태를 빠뜨릴 수 없다.
     */
    default Page<Team> findMyTeams(Long userId, Pageable pageable) {
        return findTeamsByMember(userId, MemberStatus.ACTIVE, TeamStatus.ACTIVE, pageable);
    }

    @Query("""
            SELECT t FROM Team t
            JOIN TeamMember m ON m.teamId = t.id
            WHERE m.userId = :userId
              AND m.status = :memberStatus
              AND t.status = :teamStatus
            ORDER BY m.joinedAt DESC, t.id DESC
            """)
    Page<Team> findTeamsByMember(@Param("userId") Long userId,
            @Param("memberStatus") MemberStatus memberStatus,
            @Param("teamStatus") TeamStatus teamStatus,
            Pageable pageable);

    /** 타 도메인용 일괄 조회(getSummaries) 밑작업. 해체된 팀은 결과에서 빠진다. */
    default List<Team> findActiveByIdIn(Collection<Long> teamIds) {
        return findAllByIdInAndStatus(teamIds, TeamStatus.ACTIVE);
    }

    List<Team> findAllByIdInAndStatus(Collection<Long> teamIds, TeamStatus status);
}
