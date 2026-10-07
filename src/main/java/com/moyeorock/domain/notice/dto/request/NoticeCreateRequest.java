package com.moyeorock.domain.notice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NoticeCreateRequest(
        @NotBlank(message = "공지 제목은 필수입니다.")
        @Size(max = 100, message = "공지 제목은 100자 이하여야 합니다.")
        String title,

        @NotBlank(message = "공지 내용은 필수입니다.")
        String body,

        // 명세상 선택값이고 기본값이 false다. 래퍼 타입으로 받아 "안 보냄"과 "false"를 구분하지 않고
        // null이면 false로 취급한다(isPinned() 대신 Service에서 변환).
        Boolean isPinned
) {

    public boolean pinned() {
        return Boolean.TRUE.equals(isPinned);
    }
}
