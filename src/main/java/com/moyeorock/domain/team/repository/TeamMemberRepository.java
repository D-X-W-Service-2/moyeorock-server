package com.moyeorock.domain.team.repository;

import com.moyeorock.domain.team.entity.TeamMember;
import com.moyeorock.domain.team.enums.MemberStatus;
import com.moyeorock.domain.team.enums.TeamRole;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    /** 팀의 ACTIVE 팀원 목록(팀원 목록 API·팀 상세). 합류 순으로 정렬한다. */
    default List<TeamMember> findActiveMembers(Long teamId) {
        return findAllByTeamIdAndStatusOrderByJoinedAtAscIdAsc(teamId, MemberStatus.ACTIVE);
    }

    List<TeamMember> findAllByTeamIdAndStatusOrderByJoinedAtAscIdAsc(Long teamId, MemberStatus status);

    /**
     * 상태와 무관하게 (팀, 유저)로 찾는다. 유니크 (team_id, user_id)라 최대 1건이다.
     * 권한 확인과 "탈퇴·강퇴했던 사람이 다시 들어오는" 경우의 기존 행 복구에 쓴다.
     */
    Optional<TeamMember> findByTeamIdAndUserId(Long teamId, Long userId);

    /** 현재 팀장. 팀장 위임 때 기존 팀장을 찾는 데 쓴다. */
    default Optional<TeamMember> findActiveLeader(Long teamId) {
        return findByTeamIdAndRoleAndStatus(teamId, TeamRole.LEADER, MemberStatus.ACTIVE);
    }

    Optional<TeamMember> findByTeamIdAndRoleAndStatus(Long teamId, TeamRole role, MemberStatus status);

    /** 팀 목록의 memberCount. 팀마다 count를 날리지 않고 GROUP BY 한 번으로 모은다. */
    default List<TeamMemberCount> countActiveByTeamIds(Collection<Long> teamIds) {
        return countByTeamIds(teamIds, MemberStatus.ACTIVE);
    }

    @Query("""
            SELECT m.teamId AS teamId, COUNT(m) AS count
            FROM TeamMember m
            WHERE m.teamId IN :teamIds AND m.status = :status
            GROUP BY m.teamId
            """)
    List<TeamMemberCount> countByTeamIds(@Param("teamIds") Collection<Long> teamIds,
            @Param("status") MemberStatus status);

    /** GROUP BY 결과를 담는 읽기 전용 모양. 인원이 0인 팀은 행 자체가 없으므로 호출하는 쪽이 0으로 채운다. */
    interface TeamMemberCount {

        Long getTeamId();

        long getCount();
    }
}
