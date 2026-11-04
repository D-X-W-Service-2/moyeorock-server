package com.moyeorock.domain.rehearsal.dto.response;

import java.util.List;

// from·to로 기간이 이미 제한되므로 PageResponse 대신 고정 목록 래퍼를 쓴다(dto-naming.md §0 규칙 5).
// 최상위 JSON이 배열이면 나중에 필드를 못 늘려서 객체로 감싼다.
public record RehearsalsResponse(List<RehearsalSummaryResponse> rehearsals) {
}
