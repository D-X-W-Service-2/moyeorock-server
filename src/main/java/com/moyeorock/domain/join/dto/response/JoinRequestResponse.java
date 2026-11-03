package com.moyeorock.domain.join.dto.response;

import com.moyeorock.domain.join.entity.JoinRequest;
import com.moyeorock.domain.join.enums.JoinStatus;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.TargetType;
import java.time.LocalDateTime;

// direction·targetUser는 담지 않는다(Notion 초안). applicant는 탈퇴한 신청자면 null이다.
public record JoinRequestResponse(
        Long id,
        TargetType targetType,
        JoinTargetResponse target,
        UserSummaryResponse applicant,
        Long recruitPostId,
        Instrument instrument,
        String message,
        JoinStatus status,
        LocalDateTime createdAt
) {

    public static JoinRequestResponse of(JoinRequest request, JoinTargetResponse target,
            UserSummaryResponse applicant) {
        return new JoinRequestResponse(request.getId(), request.getTargetType(), target, applicant,
                request.getRecruitPostId(), request.getInstrument(), request.getMessage(), request.getStatus(),
                request.getCreatedAt());
    }
}
