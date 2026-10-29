package com.moyeorock.domain.user.dto.request;

import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import jakarta.validation.constraints.NotNull;

/** 온보딩 등록·세션 수정 요청이 공유하는 세션 항목. customInstrument 없음(악기 ETC 제거). */
public record UserInstrumentRequest(
        @NotNull Instrument instrument,
        @NotNull Level level
) {
}
