package com.moyeorock.domain.performance.dto.request;

import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Region;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 공연 내 팀 생성. 같은 {@code teams} 테이블에 들어가서 제약은 팀 생성(TeamCreateRequest)과 같지만,
 * 요청 DTO는 도메인 간에 재사용하지 않아 따로 둔다(dto-naming.md §9). 공연은 경로로 정해진다.
 */
public record PerformanceTeamCreateRequest(
        @NotBlank(message = "팀 이름은 필수입니다.")
        @Size(max = 50, message = "팀 이름은 50자 이하여야 합니다.")
        String name,

        @Size(max = 1000, message = "팀 소개는 1,000자 이하여야 합니다.")
        String description,

        Region region,

        @Size(max = 5, message = "장르는 5개까지 고를 수 있습니다.")
        List<Genre> genres,

        // 만든 사람 본인의 세션이다. 만든 사람이 팀장이 된다.
        @NotNull(message = "세션은 필수입니다.")
        Instrument instrument
) {
}
