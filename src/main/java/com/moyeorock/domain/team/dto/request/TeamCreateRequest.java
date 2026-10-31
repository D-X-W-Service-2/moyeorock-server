package com.moyeorock.domain.team.dto.request;

import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Region;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

// instrument는 생성자 본인의 세션이다(dto-spec §2). 생성자는 @AuthUser로 받으므로 leaderId는 받지 않는다.
public record TeamCreateRequest(
        @NotBlank @Size(max = 50) String name,
        @Size(max = 1000) String description,
        Region region,
        @Size(max = 5) List<Genre> genres,
        @NotNull Instrument instrument
) {
}
