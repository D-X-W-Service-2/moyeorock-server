package com.moyeorock.domain.group.dto.request;

import com.moyeorock.domain.group.enums.GroupType;
import com.moyeorock.global.common.enums.Region;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GroupCreateRequest(
        @NotBlank(message = "모임 이름은 필수입니다.")
        @Size(max = 50, message = "모임 이름은 50자 이하여야 합니다.")
        String name,

        // 동아리 홈 위쪽에 보이는 소개라 짧게 둔다 — 긴 안내는 공지(최대 20,000자)로 쓴다(PR #67 리뷰).
        @Size(max = 1000, message = "모임 소개는 1,000자 이하여야 합니다.")
        String description,

        @NotNull(message = "모임 유형은 필수입니다.")
        GroupType type,

        @NotNull(message = "활동 지역은 필수입니다.")
        Region region,

        @Size(max = 500, message = "커버 이미지 URL은 500자 이하여야 합니다.")
        String coverImage
) {
}
