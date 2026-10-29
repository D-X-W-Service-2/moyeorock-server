package com.moyeorock.domain.notice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NoticeCreateRequest(
        @NotBlank(message = "공지 제목은 필수입니다.")
        @Size(max = 100, message = "공지 제목은 100자 이하여야 합니다.")
        String title,

        @NotBlank(message = "공지 내용은 필수입니다.")
        String body,

        // 명세상 선택값이고 기본값이 false다. primitive boolean으로 받으면 Jackson 3이 필드가 없거나
        // null일 때 역직렬화를 실패시켜(FAIL_ON_NULL_FOR_PRIMITIVES 기본 활성) 400이 나므로 래퍼로 받고,
        // null → false 변환은 pinned() 한 곳에서 한다.
        Boolean isPinned
) {

    public boolean pinned() {
        return Boolean.TRUE.equals(isPinned);
    }
}
