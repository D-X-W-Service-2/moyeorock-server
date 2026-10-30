package com.moyeorock.domain.group.dto.request;

import com.moyeorock.domain.group.enums.GroupRole;
import jakarta.validation.constraints.NotNull;

public record GroupMemberUpdateRequest(
        @NotNull(message = "변경할 역할은 필수입니다.")
        GroupRole role
) {
}
