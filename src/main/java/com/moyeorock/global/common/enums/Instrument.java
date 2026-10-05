package com.moyeorock.global.common.enums;

// 값 근거: docs/conventions/erd.md `user_instruments.instrument`
// ETC(직접 입력)는 2026-09-28 대면 회의에서 폐지했다 — custom_instrument 컬럼도 함께 삭제됨.
public enum Instrument {
    VOCAL, EL_GT, AC_GT, BASS, DRUM, KEY
}
