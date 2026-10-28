package com.moyeorock.domain.group.entity;

import com.moyeorock.domain.group.enums.GroupMemberStatus;
import com.moyeorock.domain.group.enums.GroupRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// erd.md §5: created_at·updated_at 없음(joined_at만) — BaseEntity/BaseTimeEntity 상속 없음
// (team_members와 동일 패턴).
@Entity
@Table(name = "group_members",
        indexes = @Index(name = "uk_group_members_group_id_user_id", columnList = "group_id, user_id", unique = true))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private GroupRole role;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private GroupMemberStatus status;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    public static GroupMember create(Long groupId, Long userId, GroupRole role) {
        GroupMember member = new GroupMember();
        member.groupId = groupId;
        member.userId = userId;
        member.role = role;
        member.status = GroupMemberStatus.ACTIVE;
        member.joinedAt = LocalDateTime.now();
        return member;
    }

    public void changeRole(GroupRole role) {
        this.role = role;
    }

    public void updateStatus(GroupMemberStatus status) {
        this.status = status;
    }
}
