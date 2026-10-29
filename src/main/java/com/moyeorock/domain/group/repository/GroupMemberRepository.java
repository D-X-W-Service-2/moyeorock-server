package com.moyeorock.domain.group.repository;

import com.moyeorock.domain.group.entity.GroupMember;
import com.moyeorock.domain.group.enums.GroupMemberStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 모임을 해체해도 group_members 행은 ACTIVE로 남는다. 그래서 모임원·모임장을 찾거나 세는 조회는 모임 상태까지
// 조인해서 해체된 모임을 뺀다(architecture.md §6 — 참조하는 조회에 status = ACTIVE 조건).
// 모임원 목록·존재 확인(findActiveMembers·existsBy…)은 Service가 ensureGroupExists로 먼저 막고 부른다.
public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    @Query("""
            SELECT m FROM GroupMember m, Group g
            WHERE g.id = m.groupId
              AND g.status = com.moyeorock.domain.group.enums.GroupStatus.ACTIVE
              AND m.groupId = :groupId
              AND m.userId = :userId
              AND m.status = com.moyeorock.domain.group.enums.GroupMemberStatus.ACTIVE
            """)
    Optional<GroupMember> findActiveMember(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Query("""
            SELECT m FROM GroupMember m, Group g
            WHERE g.id = m.groupId
              AND g.status = com.moyeorock.domain.group.enums.GroupStatus.ACTIVE
              AND m.groupId = :groupId
              AND m.role = com.moyeorock.domain.group.enums.GroupRole.OWNER
              AND m.status = com.moyeorock.domain.group.enums.GroupMemberStatus.ACTIVE
            """)
    Optional<GroupMember> findActiveOwner(@Param("groupId") Long groupId);

    @Query("""
            SELECT COUNT(m) FROM GroupMember m, Group g
            WHERE g.id = m.groupId
              AND g.status = com.moyeorock.domain.group.enums.GroupStatus.ACTIVE
              AND m.groupId = :groupId
              AND m.status = com.moyeorock.domain.group.enums.GroupMemberStatus.ACTIVE
            """)
    int countActiveMembers(@Param("groupId") Long groupId);

    boolean existsByGroupIdAndUserIdAndStatus(Long groupId, Long userId, GroupMemberStatus status);

    /**
     * 모임장을 맨 위에, 그다음은 가입 순. role은 {@code EnumType.STRING}으로 저장돼 있어
     * {@code ORDER BY role}로는 알파벳 순(MANAGER·MEMBER·OWNER)이 되므로 CASE로 명시한다.
     * 가입 시각이 같으면 id 순으로 정해 페이지 경계에서 중복·누락이 없게 한다.
     */
    @Query("""
            SELECT m FROM GroupMember m
            WHERE m.groupId = :groupId AND m.status = :status
            ORDER BY CASE WHEN m.role = com.moyeorock.domain.group.enums.GroupRole.OWNER THEN 0 ELSE 1 END,
                     m.joinedAt ASC, m.id ASC
            """)
    Page<GroupMember> findActiveMembers(
            @Param("groupId") Long groupId, @Param("status") GroupMemberStatus status, Pageable pageable);
}
