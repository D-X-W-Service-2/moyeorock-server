package com.moyeorock.domain.bookmark.dto.request;

import com.moyeorock.domain.bookmark.enums.BookmarkTargetType;
import jakarta.validation.constraints.NotNull;

public record BookmarkCreateRequest(
        @NotNull BookmarkTargetType targetType,
        @NotNull Long targetId
) {
}
