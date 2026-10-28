package com.moyeorock.domain.notification.enums;

// erd.md §7은 "TEAM_APPLY|TEAM_INVITE|..."로 전체 목록을 확정하지 않았다. 아래 값은 명세에
// 직접 적힌 두 개(TEAM_APPLY, TEAM_INVITE) 외에는 domains.md "팀 간 의존 — 알림 발생" 표와
// NotificationTargetType(JOIN_REQUEST·INVITATION·NOTICE·PERFORMANCE)에 근거해 추정한 것이다
// — 2026-09-29 팀 확인 완료(TEAM_MEMBER_REMOVED·GROUP_MEMBER_REMOVED·RECRUIT_POST_APPLIED·
// GROUP_INVITE 채택, SETLIST_LOCKED는 제외).
public enum NotificationType {
    TEAM_APPLY,
    TEAM_INVITE,
    GROUP_INVITE,
    JOIN_REQUEST_APPROVED,
    JOIN_REQUEST_REJECTED,
    INVITATION_ACCEPTED,
    INVITATION_DECLINED,
    TEAM_MEMBER_REMOVED,
    GROUP_MEMBER_REMOVED,
    NOTICE_CREATED,
    PERFORMANCE_CREATED,
    RECRUIT_POST_APPLIED
}
