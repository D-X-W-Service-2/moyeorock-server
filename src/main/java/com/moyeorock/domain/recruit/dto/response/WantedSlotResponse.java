package com.moyeorock.domain.recruit.dto.response;

import com.moyeorock.domain.recruit.entity.WantedSlot;
import com.moyeorock.global.common.enums.Instrument;

// appliedCount(PENDING 신청 수)는 join_requests를 세야 하는데 join 도메인이 없어 계산할 수 없다.
// "모른다"를 0(확정값)으로 내리면 프론트가 실제 값으로 믿으므로 Integer null로 둔다 —
// 키는 남고 값만 "appliedCount": null로 나간다. join 도메인이 생기면 채운다.
public record WantedSlotResponse(Instrument instrument, int count, Integer appliedCount) {

    public static WantedSlotResponse of(WantedSlot slot, Integer appliedCount) {
        return new WantedSlotResponse(slot.instrument(), slot.count(), appliedCount);
    }
}
