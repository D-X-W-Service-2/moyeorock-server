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
        indexes = {
                @Index(name = "uk_group_members_group_id_user_id", columnList = "group_id, user_id", unique = true),
                @Index(name = "idx_group_members_user_id", columnList = "user_id")
        })
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

    // 탈퇴(LEFT)·강퇴(BANNED)했던 사람이 다시 들어올 때 새 행 대신 기존 행을 되살린다 — (group_id, user_id) 유니크 때문.
    // 모임장은 위임 없이 나갈 수 없어서 기존 행의 역할은 대개 MEMBER지만, 안전하게 MEMBER로 맞추고 합류 시각도 새로 잡는다.
    public void rejoin() {
        this.status = GroupMemberStatus.ACTIVE;
        this.role = GroupRole.MEMBER;
        this.joinedAt = LocalDateTime.now();
    }

    public boolean isOwner() {
        return this.role == GroupRole.OWNER;
    }

    public void changeRole(GroupRole role) {
        this.role = role;
    }

    public void leave() {
        this.status = GroupMemberStatus.LEFT;
    }

    public void ban() {
        this.status = GroupMemberStatus.BANNED;
    }
}
