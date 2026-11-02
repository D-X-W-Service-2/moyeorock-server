package com.moyeorock.domain.join.enums;

// 초대 상태 변경(PATCH /v1/invitations/{id}/status)의 요청 값 — Notion 초안이 ACCEPTED·DECLINED·CANCELED로 정했다.
// 저장·응답은 가입 신청과 같은 JoinStatus다(수락=APPROVED, 거절=REJECTED, 취소=CANCELED).
public enum InvitationDecision {
    ACCEPTED, DECLINED, CANCELED
}
