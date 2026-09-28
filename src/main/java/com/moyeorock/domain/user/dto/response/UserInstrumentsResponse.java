package com.moyeorock.domain.user.dto.response;

import com.moyeorock.domain.user.entity.UserInstrument;
import java.util.List;

/** PUT /v1/users/me/instruments 응답. 페이징 없는 고정 목록이라 복수형 래퍼 (dto-naming 규칙 5). */
public record UserInstrumentsResponse(List<UserInstrumentResponse> instruments) {

    public static UserInstrumentsResponse from(List<UserInstrument> userInstruments) {
        return new UserInstrumentsResponse(userInstruments.stream().map(UserInstrumentResponse::from).toList());
    }
}
