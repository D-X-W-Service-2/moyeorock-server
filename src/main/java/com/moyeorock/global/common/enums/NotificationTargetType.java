package com.moyeorock.global.common.enums;

// 알림 발생 이벤트(global/event)의 페이로드라 여러 도메인이 쓰므로 global/common/enums에 둔다(절대 규칙 4).
// dto-naming.md §11: 공용 TargetType(TEAM|GROUP)·BookmarkTargetType과 값이 다르다 — 공유하지 않는다.
public enum NotificationTargetType {
    TEAM, GROUP, PERFORMANCE, NOTICE, JOIN_REQUEST, INVITATION
}
