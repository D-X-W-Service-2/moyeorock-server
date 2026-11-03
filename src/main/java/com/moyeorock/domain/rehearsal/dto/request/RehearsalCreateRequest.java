package com.moyeorock.domain.rehearsal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record RehearsalCreateRequest (
        @NotBlank @Size(max = 100) String title,
        @NotNull LocalDateTime startsAt,
        @NotNull LocalDateTime endsAt,
        @Size(max = 100) String place,
        // memo 컬럼은 TEXT(65,535바이트)다. @Size는 UTF-16 단위로 세고 한 단위가 UTF-8로 최대 3바이트라
        // 21,845자까지는 어떤 글자로 채워도 들어간다 — 공지 본문(NoticeCreateRequest)과 같이 20,000자로 정했다.
        @Size(max = 20000) String memo
) {
}
