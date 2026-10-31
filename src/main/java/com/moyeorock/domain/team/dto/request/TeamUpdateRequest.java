package com.moyeorock.domain.team.dto.request;

import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Region;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

// 전체 교체(PUT)라 보내지 않은 선택 필드는 비워진다. status는 해체 API에서만 바꾼다.
public record TeamUpdateRequest(
        @NotBlank @Size(max = 50) String name,
        @Size(max = 1000) String description,
        Region region,
        @Size(max = 5) List<Genre> genres
) {
}
