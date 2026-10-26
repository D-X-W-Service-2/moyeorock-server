package com.moyeorock.global.event;

import com.moyeorock.global.common.enums.NotificationTargetType;
import com.moyeorock.global.common.enums.NotificationType;

/**
 * 알림 발생 이벤트. 2·3·4팀 도메인은 {@code NotificationService}를 직접 호출하지 않고 이 이벤트를 발행한다
 * (architecture.md §3, domains.md 팀 간 의존). 수신은 notification 도메인이 한다.
 *
 * <p><b>트랜잭션 안에서 발행해야 한다.</b> 수신부는 발행한 쪽이 <b>커밋된 뒤에만</b> 알림을 저장하므로(롤백된 작업의
 * 알림은 남지 않는다), 트랜잭션 밖에서 발행하면 이벤트가 버려져 알림이 사라진다. 알림 저장이 실패해도 발행한 쪽의
 * 작업은 실패하지 않는다 — 대신 알림이 유실될 수 있고, 그때는 서버 로그에만 남는다.
 *
 * <p>message는 사용자에게 그대로 보이는 문장이다(예: "무명밴드에서 드럼 세션으로 초대했습니다."). 발행하는 쪽이 만든다.
 * 255자(notifications.message 컬럼 길이)를 넘으면 저장이 실패해 알림이 유실되므로 여기서 잘라 둔다 —
 * 예외를 던지면 알림 때문에 핵심 작업이 실패하게 되어서 던지지 않는다.
 */
public record NotificationEvent(
        Long userId,
        NotificationType type,
        String message,
        NotificationTargetType targetType,
        Long targetId
) {

    public static final int MESSAGE_MAX_LENGTH = 255;

    public NotificationEvent {
        if (message != null && message.length() > MESSAGE_MAX_LENGTH) {
            message = message.substring(0, MESSAGE_MAX_LENGTH);
        }
    }
}
