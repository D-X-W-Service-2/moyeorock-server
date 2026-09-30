package com.moyeorock.global.common.enums;

// 값 근거: docs/conventions/erd.md `user_instruments.instrument`
// ETC(직접 입력 세션)는 2026-09-28 팀 결정으로 제거 — 기타 악기 등록 불가, custom_instrument 컬럼도 없앰.
public enum Instrument {
    VOCAL, EL_GT, AC_GT, BASS, DRUM, KEY
}
