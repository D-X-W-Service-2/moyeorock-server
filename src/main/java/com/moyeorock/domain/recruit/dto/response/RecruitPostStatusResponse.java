package com.moyeorock.domain.recruit.dto.response;

import com.moyeorock.domain.recruit.entity.RecruitPost;
import com.moyeorock.domain.recruit.enums.RecruitStatus;

public record RecruitPostStatusResponse(Long id, RecruitStatus status) {

    public static RecruitPostStatusResponse from(RecruitPost post) {
        return new RecruitPostStatusResponse(post.getId(), post.getStatus());
    }
}
