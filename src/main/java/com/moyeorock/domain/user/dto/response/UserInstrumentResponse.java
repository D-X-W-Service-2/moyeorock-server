package com.moyeorock.domain.user.dto.response;

import com.moyeorock.domain.user.entity.UserInstrument;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;

public record UserInstrumentResponse(Long id, Instrument instrument, Level level) {

    public static UserInstrumentResponse from(UserInstrument userInstrument) {
        return new UserInstrumentResponse(
                userInstrument.getId(), userInstrument.getInstrument(), userInstrument.getLevel());
    }
}
