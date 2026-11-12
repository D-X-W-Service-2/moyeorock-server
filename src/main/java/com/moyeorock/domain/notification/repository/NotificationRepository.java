package com.moyeorock.domain.notification.repository;

import com.moyeorock.domain.notification.entity.Notification;
import com.moyeorock.global.common.enums.NotificationTargetType;
import com.moyeorock.global.common.enums.NotificationType;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserId(Long userId, Pageable pageable);

    int countByUserIdAndIsReadFalse(Long userId);

    /**
     * 본인의 안읽은 알림 전부를 읽음 처리하고 처리한 건수를 돌려준다. 벌크 UPDATE라 알림을 엔티티로 불러오지 않는다
     * (전체 읽음은 건수 상한이 없다).
     */
    @Modifying
    @Query("update Notification n set n.isRead = true where n.userId = :userId and n.isRead = false")
    int markAllAsRead(@Param("userId") Long userId);

    /**
     * 지정한 알림 중 <b>본인의</b> 안읽은 알림만 읽음 처리한다. 남의 알림 id·없는 id·이미 읽은 알림은 건수에 잡히지 않고
     * 오류도 내지 않는다 — 알림 id의 존재 여부를 드러내지 않기 위해서다.
     */
    @Modifying
    @Query("update Notification n set n.isRead = true "
            + "where n.userId = :userId and n.isRead = false and n.id in :ids")
    int markAsRead(@Param("userId") Long userId, @Param("ids") Collection<Long> ids);

    /**
     * 신청·초대 한 건을 가리키는 알림 중 지정한 타입의 안읽은 알림을 받은 사람과 관계없이 읽음 처리한다(#182).
     * 신청·초대가 처리되면 "처리해 달라"는 알림은 더 볼 필요가 없다.
     */
    @Modifying
    @Query("update Notification n set n.isRead = true "
            + "where n.targetType = :targetType and n.targetId = :targetId and n.type in :types and n.isRead = false")
    int markTargetAsRead(@Param("targetType") NotificationTargetType targetType, @Param("targetId") Long targetId,
            @Param("types") Collection<NotificationType> types);
}
