package com.moyeorock.domain.performance.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

// 확정할 곡과 순서. 팀들이 넘긴 곡만 넣을 수 있고, 요청에 없는 곡은 셋리스트에서 빠진다.
// sortOrder는 1부터 빈틈없이 매긴다(1..n). 겹치거나 비면 400 — SetlistService.confirm이 검사한다.
public record SetlistConfirmRequest(
        @NotEmpty(message = "확정할 곡 목록은 비어 있을 수 없습니다.")
        List<@NotNull(message = "확정할 곡은 비어 있을 수 없습니다.") @Valid Item> items
) {

    public record Item(
            @NotNull(message = "팀 아이디는 필수입니다.")
            Long teamId,

            @NotNull(message = "곡 아이디는 필수입니다.")
            Long songId,

            int sortOrder
    ) {
    }
}
