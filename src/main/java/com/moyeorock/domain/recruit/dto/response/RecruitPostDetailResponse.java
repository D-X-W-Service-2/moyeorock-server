package com.moyeorock.domain.recruit.dto.response;

import com.moyeorock.domain.recruit.entity.RecruitPost;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import com.moyeorock.domain.recruit.enums.RecruitStatus;
import com.moyeorock.domain.join.enums.JoinStatus;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.common.enums.TargetType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// author는 탈퇴한 작성자면 null이다(getSummaries가 탈퇴 회원을 뺀다). myJoinRequest는 비로그인이거나
// 그 공고에 신청한 적이 없으면 null이다(dto-spec §4).
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
        MyJoinRequest myJoinRequest,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /** 보는 사람이 그 공고에 낸 가장 최근 신청. join 도메인 DTO를 끌어오지 않으려고 필요한 2개 필드만 둔다. */
    public record MyJoinRequest(Long id, JoinStatus status) {
    }

    public static RecruitPostDetailResponse of(RecruitPost post, RecruitPostTargetResponse target,
            UserSummaryResponse author, boolean canEdit, Map<Instrument, Long> appliedCounts,
            MyJoinRequest myJoinRequest) {
        return new RecruitPostDetailResponse(
                post.getId(),
                post.getTargetType(),
                target,
                author,
                post.getTitle(),
                post.getBody(),
                post.getWantedSlots().stream()
                        .map(slot -> WantedSlotResponse.of(slot, appliedCounts.getOrDefault(slot.instrument(), 0L).intValue()))
                        .toList(),
                post.getRegion(),
                post.getStatus(),
                canEdit,
                myJoinRequest,
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
