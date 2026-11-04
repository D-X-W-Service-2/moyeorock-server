package com.moyeorock.domain.setlist.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;

// 기존 선택곡 전체를 이 목록으로 바꾼다(빈 목록이면 전부 삭제). 같은 곡 중복은 Service가 400으로 막는다.
public record TeamSongsSaveRequest(
        @NotNull(message = "곡 목록은 필수입니다.")
        List<@NotNull(message = "곡 아이디는 비어 있을 수 없습니다.") Long> songIds
) {
}
