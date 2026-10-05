package com.moyeorock.domain.notification.entity;

import com.moyeorock.domain.notification.enums.NotificationTargetType;
import com.moyeorock.domain.notification.enums.NotificationType;
import com.moyeorock.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// type 값 목록은 erd.md §7("TEAM_APPLY|TEAM_INVITE|...")이 확정하지 않은 것을 팀 확인을 받아
// NotificationType으로 채웠다(2026-09-29, 근거는 그 enum 파일 상단 주석 참고). target_type/
// target_id는 여전히 다형성이다(erd.md 관계 절 — exclusive-arc로 전환 안 된 유일한 곳). 다만
// NotificationTargetType 값은 이미 확정돼 있어 그건 그대로 enum으로 둔다.
@Entity
@Table(name = "notifications",
        indexes = @Index(name = "idx_notifications_user_id_is_read_created_at",
                columnList = "user_id, is_read, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private NotificationType type;

    @Column(length = 255)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 20)
    private NotificationTargetType targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "is_read")
    private boolean isRead;

    public static Notification create(Long userId, NotificationType type, String message,
            NotificationTargetType targetType, Long targetId) {
        Notification notification = new Notification();
        notification.userId = userId;
        notification.type = type;
        notification.message = message;
        notification.targetType = targetType;
        notification.targetId = targetId;
        notification.isRead = false;
        return notification;
    }

    public void markAsRead() {
        this.isRead = true;
    }
}
