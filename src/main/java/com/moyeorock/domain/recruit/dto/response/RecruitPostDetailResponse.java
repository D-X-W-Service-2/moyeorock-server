package com.moyeorock.domain.recruit.dto.response;

import com.moyeorock.domain.recruit.entity.RecruitPost;
import com.moyeorock.domain.recruit.enums.RecruitStatus;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.common.enums.TargetType;
import java.time.LocalDateTime;
import java.util.List;

// dto-spec.md §4 예시에는 author(UserSummary)·myJoinRequest 필드도 있지만 여기 없다:
// - author: UserSummaryResponse(1팀)가 아직 없다. user 도메인 생기면 추가.
// - myJoinRequest: MyJoinRequest(Long id, JoinStatus status) — JoinStatus가 join 도메인
//   소유라 아직 타입 자체가 없다. join 도메인 생기면 추가.
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
                RecruitPostTargetResponse.idOnly(post.getTargetId()),
                post.getTitle(),
                post.getBody(),
                post.getWantedSlots().stream()
                        .map(slot -> new WantedSlotResponse(slot.instrument(), slot.count(), null))
                        .toList(),
                post.getRegion(),
                post.getStatus(),
                viewerId != null && post.isAuthor(viewerId),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
