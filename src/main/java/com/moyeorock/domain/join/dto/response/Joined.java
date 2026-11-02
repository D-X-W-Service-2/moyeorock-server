package com.moyeorock.domain.join.dto.response;

import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.TargetType;

/** 가입 처리된 정보. 신청 승인·초대 수락 응답({@link JoinApprovedResponse})이 쓴다. */
public record Joined(TargetType targetType, Long targetId, String role, Instrument instrument) {
}
