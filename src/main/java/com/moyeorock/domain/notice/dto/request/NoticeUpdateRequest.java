package com.moyeorock.domain.notice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NoticeUpdateRequest(
        @NotBlank(message = "공지 제목은 필수입니다.")
        @Size(max = 100, message = "공지 제목은 100자 이하여야 합니다.")
        String title,

        @NotBlank(message = "공지 내용은 필수입니다.")
        String body,

        Boolean isPinned
) {

    public boolean pinned() {
        return Boolean.TRUE.equals(isPinned);
    }
}
