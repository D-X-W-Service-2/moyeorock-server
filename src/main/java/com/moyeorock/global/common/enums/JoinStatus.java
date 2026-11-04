package com.moyeorock.global.common.enums;

// 값 근거: docs/conventions/erd.md `join_requests.status`
// join이 소유하지만 recruit 공고 상세(내 신청 상태)도 내보내서 공용으로 둔다(CLAUDE.md 절대 규칙 4)
public enum JoinStatus {
    PENDING, APPROVED, REJECTED, CANCELED
}
