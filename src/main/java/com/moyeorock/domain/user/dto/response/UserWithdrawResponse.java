package com.moyeorock.domain.user.dto.response;

import java.time.LocalDateTime;

public record UserWithdrawResponse(LocalDateTime withdrawnAt) {
}
