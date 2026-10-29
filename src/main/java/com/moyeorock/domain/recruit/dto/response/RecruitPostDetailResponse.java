package com.moyeorock.domain.recruit.dto.response;

import com.moyeorock.domain.recruit.entity.RecruitPost;
import com.moyeorock.domain.recruit.enums.RecruitStatus;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.common.enums.TargetType;
import java.time.LocalDateTime;
import java.util.List;

// Notion 명세에는 author(UserSummaryResponse, 1팀 #57)·myJoinRequest(join 도메인)도 있지만 아직
// 채울 수 없어 필드 자체를 뺐다(null로 두면 "작성자 없음/신청 이력 없음"으로 읽혀 오해를 만든다).
// 해당 도메인이 머지되면 추가한다.
public record RecruitPostDetailResponse(
        Long id,
        TargetType targetType,
        RecruitPostTargetResponse target,
        String title,
        String body,
        List<WantedSlotResponse> wantedSlots,
        Region region,
        RecruitStatus status,
        boolean canEdit,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static RecruitPostDetailResponse of(RecruitPost post, Long viewerId) {
        return new RecruitPostDetailResponse(
                post.getId(),
                post.getTargetType(),
                RecruitPostTargetResponse.from(post),
                post.getTitle(),
                post.getBody(),
                post.getWantedSlots().stream().map(slot -> WantedSlotResponse.of(slot, null)).toList(),
                post.getRegion(),
                post.getStatus(),
                post.isAuthor(viewerId),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
