package com.moyeorock.domain.join.dto.response;

import com.moyeorock.domain.join.entity.JoinRequest;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.JoinStatus;
import com.moyeorock.global.common.enums.TargetType;
import java.time.LocalDateTime;

// inviter·invitee는 탈퇴한 사용자면 null이다.
public record InvitationResponse(
        Long id,
        TargetType targetType,
        JoinTargetResponse target,
        UserSummaryResponse inviter,
        UserSummaryResponse invitee,
        Instrument instrument,
        String message,
        JoinStatus status,
        LocalDateTime createdAt
) {

    public static InvitationResponse of(JoinRequest invitation, JoinTargetResponse target,
            UserSummaryResponse inviter, UserSummaryResponse invitee) {
        return new InvitationResponse(invitation.getId(), invitation.getTargetType(), target, inviter, invitee,
                invitation.getInstrument(), invitation.getMessage(), invitation.getStatus(),
                invitation.getCreatedAt());
    }
}
