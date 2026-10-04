package com.moyeorock.domain.group.repository;

import com.moyeorock.domain.group.entity.GroupMember;
import com.moyeorock.domain.group.enums.GroupMemberStatus;
import com.moyeorock.domain.group.enums.GroupRole;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    Optional<GroupMember> findByGroupIdAndUserIdAndStatus(Long groupId, Long userId, GroupMemberStatus status);

    Optional<GroupMember> findByGroupIdAndRoleAndStatus(Long groupId, GroupRole role, GroupMemberStatus status);

    int countByGroupIdAndStatus(Long groupId, GroupMemberStatus status);

    boolean existsByGroupIdAndUserIdAndStatus(Long groupId, Long userId, GroupMemberStatus status);

    /**
     * 모임장을 맨 위에, 그다음은 가입 순. role은 {@code EnumType.STRING}으로 저장돼 있어
     * {@code ORDER BY role}로는 알파벳 순(MANAGER·MEMBER·OWNER)이 되므로 CASE로 명시한다.
     */
    @Query("""
            SELECT m FROM GroupMember m
            WHERE m.groupId = :groupId AND m.status = :status
            ORDER BY CASE WHEN m.role = com.moyeorock.domain.group.enums.GroupRole.OWNER THEN 0 ELSE 1 END,
                     m.joinedAt ASC
            """)
    Page<GroupMember> findActiveMembers(
            @Param("groupId") Long groupId, @Param("status") GroupMemberStatus status, Pageable pageable);
}
