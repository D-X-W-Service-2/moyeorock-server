package com.moyeorock.domain.join.repository;

import com.moyeorock.domain.join.entity.JoinRequest;
import com.moyeorock.domain.join.enums.JoinDirection;
import com.moyeorock.global.common.enums.JoinStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JoinRequestRepository extends JpaRepository<JoinRequest, Long> {

    /** 상태 변경 때 같은 행을 두 번 처리하지 않도록 잠그고 읽는다(승인과 취소가 동시에 들어오는 경우). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM JoinRequest r WHERE r.id = :id")
    Optional<JoinRequest> findByIdForUpdate(@Param("id") Long id);

    /** 회원 탈퇴 정리용. 이 사람이 신청자이거나 초대받은 사람인 대기 건을 id 순으로 잠그고 읽는다(승인·취소와 겹쳐도 한 번만 처리). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM JoinRequest r WHERE r.userId = :userId AND r.status = com.moyeorock.domain.join.enums.JoinStatus.PENDING ORDER BY r.id")
    List<JoinRequest> findPendingByUserIdForUpdate(@Param("userId") Long userId);

    /**
     * 중복 검사. 신청과 초대를 가리지 않는다 — 둘이 동시에 있으면 가입 경로가 둘이 되기 때문이다(domains.md).
     * 팀 대상과 모임 대상은 컬럼이 달라 메서드를 나눴다.
     */
    boolean existsByUserIdAndTargetTeamIdAndStatus(Long userId, Long targetTeamId, JoinStatus status);

    boolean existsByUserIdAndTargetGroupIdAndStatus(Long userId, Long targetGroupId, JoinStatus status);

    /**
     * 목록 조회 공통 쿼리. userId·teamId·groupId는 null이면 걸지 않는다(Long 파라미터라 타입 추론에 문제가 없다).
     * status는 null 대신 호출하는 쪽이 "전체 상태" 목록을 넘긴다. 정렬은 Pageable이 정한다.
     */
    @Query("""
            SELECT r FROM JoinRequest r
            WHERE r.direction = :direction
              AND (:userId IS NULL OR r.userId = :userId)
              AND (:teamId IS NULL OR r.targetTeamId = :teamId)
              AND (:groupId IS NULL OR r.targetGroupId = :groupId)
              AND r.status IN :statuses
            """)
    Page<JoinRequest> search(@Param("direction") JoinDirection direction, @Param("userId") Long userId,
            @Param("teamId") Long teamId, @Param("groupId") Long groupId,
            @Param("statuses") Collection<JoinStatus> statuses, Pageable pageable);

    /** 공고의 세션별 대기(PENDING) 신청 수. 결과는 [instrument, count] 쌍이다. */
    @Query("""
            SELECT r.instrument, COUNT(r) FROM JoinRequest r
            WHERE r.recruitPostId = :postId AND r.status = com.moyeorock.global.common.enums.JoinStatus.PENDING
            GROUP BY r.instrument
            """)
    List<Object[]> countPendingByInstrument(@Param("postId") Long postId);

    /** 그 공고에 낸 내 신청 중 가장 최근 것. */
    Optional<JoinRequest> findFirstByRecruitPostIdAndUserIdAndDirectionOrderByIdDesc(
            Long recruitPostId, Long userId, JoinDirection direction);
}
