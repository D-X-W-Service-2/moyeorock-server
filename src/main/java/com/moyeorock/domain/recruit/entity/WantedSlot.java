package com.moyeorock.domain.recruit.entity;

import com.moyeorock.global.common.enums.Instrument;

// wanted_slots JSON 컬럼 한 항목의 엔티티 쪽 표현. API 계층의 WantedSlotRequest/Response와
// 분리해 둔다 — 엔티티가 dto.request 타입을 직접 들고 있지 않게 하기 위함(conventions.md §6).
public record WantedSlot(Instrument instrument, int count) {
}
