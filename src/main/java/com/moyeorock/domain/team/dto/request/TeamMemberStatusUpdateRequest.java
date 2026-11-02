package com.moyeorock.domain.team.dto.request;

import com.moyeorock.domain.team.enums.MemberStatus;
import jakarta.validation.constraints.NotNull;

// LEFT(본인 탈퇴) 또는 REMOVED(강퇴)만 허용한다. ACTIVE 등 나머지 값은 Service에서 거절한다.
public record TeamMemberStatusUpdateRequest(@NotNull MemberStatus status) {
}
