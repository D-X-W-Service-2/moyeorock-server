package com.moyeorock.domain.performance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

// 생성 요청과 필드가 같지만 분리해 둔다 — 검증 규칙이 갈리는 순간 하나로 묶인 DTO가 발목을 잡는다
// (dto-naming.md 지켜야 할 6가지). 길이·필수 여부를 정한 이유는 PerformanceCreateRequest와 같다.
public record PerformanceUpdateRequest(
        @NotBlank(message = "공연 제목은 필수입니다.")
        @Size(max = 100, message = "공연 제목은 100자 이하여야 합니다.")
        String title,

        @Size(max = 1000, message = "공연 설명은 1,000자 이하여야 합니다.")
        String description,

        @NotNull(message = "공연 일시는 필수입니다.")
        LocalDateTime performedAt,

        @NotBlank(message = "공연 장소는 필수입니다.")
        @Size(max = 100, message = "공연 장소는 100자 이하여야 합니다.")
        String venue,

        @Size(max = 500, message = "포스터 이미지 URL은 500자 이하여야 합니다.")
        String posterImage
) {
}
