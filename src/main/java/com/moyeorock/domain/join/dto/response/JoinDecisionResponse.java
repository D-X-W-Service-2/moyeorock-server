package com.moyeorock.domain.join.dto.response;

import com.moyeorock.domain.join.entity.JoinRequest;
import com.moyeorock.domain.join.enums.JoinStatus;
import java.time.LocalDateTime;

/** 신청·초대의 거절·취소 공용 응답. */
public record JoinDecisionResponse(Long id, JoinStatus status, LocalDateTime decidedAt)
        implements JoinResultResponse {

    public static JoinDecisionResponse from(JoinRequest request) {
        return new JoinDecisionResponse(request.getId(), request.getStatus(), request.getDecidedAt());
    }
}
