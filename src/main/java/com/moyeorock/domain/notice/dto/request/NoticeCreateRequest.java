package com.moyeorock.domain.notice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NoticeCreateRequest(
        @NotBlank(message = "공지 제목은 필수입니다.")
        @Size(max = 100, message = "공지 제목은 100자 이하여야 합니다.")
        String title,

        // body 컬럼은 TEXT(65,535바이트)다. @Size는 UTF-16 단위로 세고 한 단위가 UTF-8로 최대 3바이트라
        // 21,845자까지는 어떤 글자로 채워도 들어간다 — 그 아래에서 20,000자로 정했다(PR #67 본문).
        @NotBlank(message = "공지 내용은 필수입니다.")
        @Size(max = 20000, message = "공지 내용은 20,000자 이하여야 합니다.")
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
