package com.moyeorock.domain.group.repository;

import com.moyeorock.domain.group.entity.Group;
import com.moyeorock.domain.group.enums.GroupStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 해체된 모임은 없는 모임으로 본다 — 모임을 찾을 때는 findById·existsById 대신 status를 받는 메서드를 쓴다.
public interface GroupRepository extends JpaRepository<Group, Long> {

    Optional<Group> findByIdAndStatus(Long id, GroupStatus status);

    boolean existsByIdAndStatus(Long id, GroupStatus status);

    /**
     * 쓰기 전용 잠금 조회. 위임·탈퇴·강퇴·해체가 같은 모임에 동시에 오면 모임장이 0명·2명이 되거나
     * 한쪽 변경(전 컬럼 UPDATE)이 다른 쪽을 덮으므로, 모임 행을 잠가 한 번에 하나씩만 돌게 한다(PR #67 리뷰,
     * UserRepository.findByIdAndStatusForUpdate와 같은 방식). 트랜잭션의 첫 조회로 불러야 이후 조회가
     * 앞선 트랜잭션의 커밋을 본다(MySQL REPEATABLE READ는 첫 일반 조회 시점에 스냅샷을 잡는다).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM Group g WHERE g.id = :id AND g.status = :status")
    Optional<Group> findByIdAndStatusForUpdate(@Param("id") Long id, @Param("status") GroupStatus status);

    /**
     * 내가 ACTIVE 모임원인, 해체되지 않은 모임과 그 모임에서의 내 역할. 가입 최신순.
     * 각 행은 {@code [Group, GroupRole]}이다 — 조인한 모임원 행에 역할이 이미 있어 따로 조회하지 않는다.
     * 같은 도메인 테이블끼리라 연관관계 매핑 없이 조인한다.
     * keyword는 모임 이름 부분 일치이고 {@code %}·{@code _}를 글자 그대로 찾는다(파생 쿼리 Containing과 같은 동작).
     * SpEL {@code escape()}는 null 인자를 받으면 타입을 못 정해 NPE가 나서 null일 때는 부르지 않는다.
     */
    @Query("""
            SELECT g, m.role FROM Group g, GroupMember m
            WHERE m.groupId = g.id
              AND m.userId = :userId
              AND m.status = com.moyeorock.domain.group.enums.GroupMemberStatus.ACTIVE
              AND g.status = com.moyeorock.domain.group.enums.GroupStatus.ACTIVE
              AND (:keyword IS NULL
                   OR g.name LIKE CONCAT('%', :#{#keyword == null ? null : escape(#keyword)}, '%')
                      ESCAPE :#{escapeCharacter()})
            ORDER BY m.joinedAt DESC, g.id DESC
            """)
    Page<Object[]> findMyGroups(@Param("userId") Long userId, @Param("keyword") String keyword, Pageable pageable);
}
