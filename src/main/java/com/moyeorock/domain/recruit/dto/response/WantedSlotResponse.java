package com.moyeorock.domain.recruit.dto.response;

import com.moyeorock.domain.recruit.entity.WantedSlot;
import com.moyeorock.global.common.enums.Instrument;

// appliedCount는 그 세션의 대기(PENDING) 신청 수다. 신청이 없으면 0이다.
public record WantedSlotResponse(Instrument instrument, int count, int appliedCount) {

    public static WantedSlotResponse of(WantedSlot slot, int appliedCount) {
        return new WantedSlotResponse(slot.instrument(), slot.count(), appliedCount);
    }
}
