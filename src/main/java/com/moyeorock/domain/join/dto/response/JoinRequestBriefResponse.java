package com.moyeorock.domain.join.dto.response;

import com.moyeorock.domain.join.entity.JoinRequest;
import com.moyeorock.global.common.enums.JoinStatus;

/** 다른 도메인(공고 상세의 {@code myJoinRequest})이 내 신청 현황을 표시할 때 쓰는 최소 정보. */
public record JoinRequestBriefResponse(Long id, JoinStatus status) {

    public static JoinRequestBriefResponse from(JoinRequest request) {
        return new JoinRequestBriefResponse(request.getId(), request.getStatus());
    }
}
