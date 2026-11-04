package com.moyeorock.domain.recruit.dto.response;

import com.moyeorock.domain.recruit.entity.RecruitPost;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import com.moyeorock.domain.recruit.enums.RecruitStatus;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.common.enums.TargetType;
import java.time.LocalDateTime;
import java.util.List;

// Notion 명세의 myJoinRequest는 join 도메인이 생겨야 채울 수 있어 아직 필드 자체를 뺐다(null로 두면
// "신청 이력 없음"으로 읽혀 오해를 만든다). author는 탈퇴한 작성자면 null이다(getSummaries가 탈퇴 회원을 뺀다).
public record RecruitPostDetailResponse(
        Long id,
        TargetType targetType,
        RecruitPostTargetResponse target,
        UserSummaryResponse author,
        String title,
        String body,
        List<WantedSlotResponse> wantedSlots,
        Region region,
        RecruitStatus status,
        boolean canEdit,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static RecruitPostDetailResponse of(RecruitPost post, RecruitPostTargetResponse target,
            UserSummaryResponse author, boolean canEdit) {
        return new RecruitPostDetailResponse(
                post.getId(),
                post.getTargetType(),
                target,
                author,
                post.getTitle(),
                post.getBody(),
                post.getWantedSlots().stream().map(slot -> WantedSlotResponse.of(slot, null)).toList(),
                post.getRegion(),
                post.getStatus(),
                canEdit,
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
