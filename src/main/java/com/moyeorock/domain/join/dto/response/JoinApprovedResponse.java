package com.moyeorock.domain.join.dto.response;

import com.moyeorock.domain.join.entity.JoinRequest;
import com.moyeorock.global.common.enums.JoinStatus;
import java.time.LocalDateTime;

/** 신청 승인·초대 수락 공용 응답. 수락해도 status는 APPROVED다(Notion 초안). */
public record JoinApprovedResponse(Long id, JoinStatus status, LocalDateTime decidedAt, Joined joined)
        implements JoinResultResponse {

    public static JoinApprovedResponse of(JoinRequest request, String role) {
        return new JoinApprovedResponse(request.getId(), request.getStatus(), request.getDecidedAt(),
                new Joined(request.getTargetType(), request.getTargetId(), role, request.getInstrument()));
    }
}
