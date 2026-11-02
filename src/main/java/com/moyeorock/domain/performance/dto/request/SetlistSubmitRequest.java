package com.moyeorock.domain.performance.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;

// 그 팀이 이 공연에 넘긴 곡 전체를 이 목록으로 바꾼다(빈 목록이면 모두 뺀다). 팀 선택곡 안에서만 고를 수 있다.
public record SetlistSubmitRequest(
        @NotNull(message = "곡 목록은 필수입니다.")
        List<@NotNull(message = "곡 아이디는 비어 있을 수 없습니다.") Long> songIds
) {
}
