package com.moyeorock.domain.team.repository;

import com.moyeorock.domain.team.entity.TeamMember;
import com.moyeorock.domain.team.enums.MemberStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * 내가 속한 팀 전부(내 활동용). 팀원 상태가 ACTIVE인 행만 최근 합류 순으로 돌려준다.
     * 팀이 ACTIVE인지는 이 쿼리가 아니라 호출하는 쪽이 팀 조회로 거른다.
     */
    default List<TeamMember> findActiveMembershipsOf(Long userId) {
        return findAllByUserIdAndStatusOrderByJoinedAtDescIdDesc(userId, MemberStatus.ACTIVE);
    }

    List<TeamMember> findAllByUserIdAndStatusOrderByJoinedAtDescIdDesc(Long userId, MemberStatus status);

    /**
     * 그 사람이 주어진 팀들에서 가진 ACTIVE 팀원 행을 잠그고 읽는다(모임 탈퇴·강퇴, 회원 탈퇴 때 팀에서 빼기).
     * 잠금 읽기는 트랜잭션 시작 이후의 낡은 화면이 아니라 가장 최근에 커밋된 값을 읽으므로, 같은 트랜잭션에서 앞서
     * 팀원 행을 읽었더라도 그 사이에 바뀐 역할(팀장 위임 등)을 놓치지 않는다. id 순으로 고정한다.
     */
    default List<TeamMember> findActiveByUserIdAndTeamIdInForUpdate(Long userId, Collection<Long> teamIds) {
        return findAllByUserIdAndTeamIdInAndStatusOrderByIdAsc(userId, teamIds, MemberStatus.ACTIVE);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<TeamMember> findAllByUserIdAndTeamIdInAndStatusOrderByIdAsc(Long userId, Collection<Long> teamIds,
            MemberStatus status);

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
