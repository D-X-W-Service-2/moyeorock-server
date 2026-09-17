package com.moyeorock.domain.recruit.dto.response;

import com.moyeorock.domain.recruit.enums.RecruitStatus;

public record RecruitPostStatusResponse(Long id, RecruitStatus status) {
}
