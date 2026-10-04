package com.moyeorock.domain.group.dto.request;

import com.moyeorock.domain.group.enums.GroupType;
import com.moyeorock.global.common.enums.Region;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 생성 요청과 필드가 같지만 분리해 둔다 — 수정에서 빠질 필드(예: type 고정)가 생기면
// 생성까지 흔들리기 때문이다(dto-naming.md 지켜야 할 6가지).
public record GroupUpdateRequest(
        @NotBlank(message = "모임 이름은 필수입니다.")
        @Size(max = 50, message = "모임 이름은 50자 이하여야 합니다.")
        String name,

        String description,

        @NotNull(message = "모임 유형은 필수입니다.")
        GroupType type,

        @NotNull(message = "활동 지역은 필수입니다.")
        Region region,

        @Size(max = 500, message = "커버 이미지 URL은 500자 이하여야 합니다.")
        String coverImage
) {
}
