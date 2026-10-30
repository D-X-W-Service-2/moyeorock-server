package com.moyeorock.domain.performance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record PerformanceCreateRequest(
        @NotBlank(message = "공연 제목은 필수입니다.")
        @Size(max = 100, message = "공연 제목은 100자 이하여야 합니다.")
        String title,

        // 모임 소개와 같은 기준 — 긴 안내는 공지(최대 20,000자)로 쓴다.
        @Size(max = 1000, message = "공연 설명은 1,000자 이하여야 합니다.")
        String description,

        @NotNull(message = "공연 일시는 필수입니다.")
        LocalDateTime performedAt,

        // erd.md는 NULL 허용이지만 노션 API 초안(2026-10-08)이 필수로 정했다 — 애플리케이션에서만 막는다.
        @NotBlank(message = "공연 장소는 필수입니다.")
        @Size(max = 100, message = "공연 장소는 100자 이하여야 합니다.")
        String venue,

        // 파일 API로 받은 fileUrl을 그대로 저장한다(erd.md §17).
        @Size(max = 500, message = "포스터 이미지 URL은 500자 이하여야 합니다.")
        String posterImage
) {
}
