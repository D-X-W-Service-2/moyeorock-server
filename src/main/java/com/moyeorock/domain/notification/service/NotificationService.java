package com.moyeorock.domain.notification.service;

import com.moyeorock.domain.notification.dto.request.NotificationReadRequest;
import com.moyeorock.domain.notification.dto.response.NotificationReadResponse;
import com.moyeorock.domain.notification.dto.response.NotificationsResponse;
import com.moyeorock.domain.notification.entity.Notification;
import com.moyeorock.domain.notification.repository.NotificationRepository;
import com.moyeorock.global.common.enums.NotificationTargetType;
import com.moyeorock.global.common.enums.NotificationType;
import com.moyeorock.global.event.NotificationEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 알림은 다른 도메인이 이 Service를 직접 호출하지 않고 {@link NotificationEvent}로만 전달한다(architecture.md §3).
 * 수신부는 {@link #saveOnEvent}다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    // 명세의 알림 목록에는 정렬 파라미터가 없다 — 최신순 고정. 클라이언트 sort를 그대로 받으면
    // 엔티티에 없는 속성이 들어왔을 때 500이 난다.
    private static final Sort LATEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");
    // 신청·초대를 처리해 달라고 알리는 타입. 처리되면 읽음으로 바꾼다(#182).
    private static final Map<NotificationTargetType, Set<NotificationType>> REQUEST_TYPES = Map.of(
            NotificationTargetType.JOIN_REQUEST, Set.of(NotificationType.TEAM_APPLY, NotificationType.RECRUIT_POST_APPLIED),
            NotificationTargetType.INVITATION, Set.of(NotificationType.TEAM_INVITE, NotificationType.GROUP_INVITE));

    private final NotificationRepository notificationRepository;

    /** 목록 + 안읽은 수. 두 쿼리의 오차는 다음 요청에서 맞춰지므로 스냅샷 트랜잭션을 열지 않는다. */
    public NotificationsResponse getNotifications(Long userId, Pageable pageable) {
        Pageable latestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), LATEST_FIRST);
        Page<Notification> page = notificationRepository.findByUserId(userId, latestFirst);
        int unreadCount = notificationRepository.countByUserIdAndIsReadFalse(userId);
        return NotificationsResponse.of(unreadCount, page);
    }

    /** 안 읽은 알림 수(대시보드용). 단일 쿼리라 트랜잭션을 걸지 않는다. */
    public int countUnread(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    /**
     * 읽음 처리. {@code ids}가 null이거나 비어 있으면 전체 읽음, 아니면 지정한 알림만이다.
     * 요청 바디 자체가 없어도({@code request == null}) 전체 읽음이다.
     */
    @Transactional
    public NotificationReadResponse markAsRead(Long userId, NotificationReadRequest request) {
        List<Long> ids = request == null ? null : request.ids();
        int readCount = (ids == null || ids.isEmpty())
                ? notificationRepository.markAllAsRead(userId)
                : notificationRepository.markAsRead(userId, new HashSet<>(ids));
        return new NotificationReadResponse(readCount, notificationRepository.countByUserIdAndIsReadFalse(userId));
    }

    /**
     * 신청·초대 한 건이 처리됐을 때(승인·거절·수락·취소) 그 건을 알린 "처리해 달라"는 알림을 읽음 처리한다(#182).
     * 신청은 {@code TEAM_APPLY}·{@code RECRUIT_POST_APPLIED}, 초대는 {@code TEAM_INVITE}·{@code GROUP_INVITE}다.
     * 처리 결과 알림(승인·거절 등)은 건드리지 않는다. 호출한 쪽의 트랜잭션에 참여한다.
     */
    @Transactional
    public void markRequestNotificationsRead(NotificationTargetType targetType, Long targetId) {
        notificationRepository.markTargetAsRead(targetType, targetId, REQUEST_TYPES.get(targetType));
    }

    /**
     * 알림 발생 이벤트 수신. 발행한 쪽이 <b>커밋된 뒤에만</b> 별도 트랜잭션으로 저장한다.
     * 따라서 알림 저장이 실패해도 발행한 쪽의 작업은 실패하지 않는다 — 대신 알림이 유실될 수 있고 그때는 로그에만 남는다.
     *
     * <p>{@code AFTER_COMMIT} 단계에서는 발행 쪽 트랜잭션이 이미 끝나 있어 {@code REQUIRES_NEW}가 없으면
     * 저장이 조용히 사라진다(커밋되지 않음). 트랜잭션 밖에서 발행된 이벤트도 호출되지 않는다.
     * 자세한 선택 근거는 docs/plans/notification-event.md.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveOnEvent(NotificationEvent event) {
        if (event.userId() == null || event.type() == null || event.message() == null
                || event.targetType() == null || event.targetId() == null) {
            log.error("알림 이벤트에 필수 값이 없어 저장하지 않는다: {}", event);
            return;
        }
        try {
            notificationRepository.save(Notification.create(
                    event.userId(), event.type(), event.message(), event.targetType(), event.targetId()));
        } catch (RuntimeException e) {
            log.error("알림 저장 실패(유실): userId={}, type={}, targetType={}, targetId={}",
                    event.userId(), event.type(), event.targetType(), event.targetId(), e);
            throw e;
        }
    }
}
