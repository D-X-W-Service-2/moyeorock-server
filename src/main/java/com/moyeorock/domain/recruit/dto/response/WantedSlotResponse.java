package com.moyeorock.domain.recruit.dto.response;

import com.moyeorock.global.common.enums.Instrument;

// dto-naming.md §5 보조 DTO: WantedSlotRequest에 appliedCount(PENDING 신청 수, 계산값)를 더한 것.
// appliedCount는 join_requests를 세야 하는데 join 도메인이 아직 없어 계산할 수 없다.
// "모른다"를 0(신청자 없음, 사실이 아닐 수 있는 확정값)으로 흘리면 프론트가 실제 데이터로 믿을
// 위험이 있어, int가 아니라 Integer로 두고 null(계산 불가)을 명시적으로 표현한다.
// application.yml의 jackson.default-property-inclusion: non_null 덕에 null이면 이 키 자체가
// JSON에서 빠진다. join 도메인이 생기면 JoinRequestService로 실제 값을 채운다.
public record WantedSlotResponse(Instrument instrument, int count, Integer appliedCount) {
}
