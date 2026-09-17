package com.moyeorock.domain.recruit.dto.response;

import com.moyeorock.domain.recruit.entity.RecruitPost;
import com.moyeorock.domain.recruit.entity.WantedSlot;
import com.moyeorock.domain.recruit.enums.RecruitStatus;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.common.enums.TargetType;
import java.time.LocalDateTime;
import java.util.List;

public record RecruitPostSummaryResponse(
        Long id,
        TargetType targetType,
        RecruitPostTargetResponse target,
        String title,
        List<WantedSlotSummary> wantedSlots,
        Region region,
        RecruitStatus status,
        LocalDateTime createdAt
) {

    public static RecruitPostSummaryResponse from(RecruitPost post) {
        return new RecruitPostSummaryResponse(
                post.getId(),
                post.getTargetType(),
                RecruitPostTargetResponse.idOnly(post.getTargetId()),
                post.getTitle(),
                post.getWantedSlots().stream().map(WantedSlotSummary::from).toList(),
                post.getRegion(),
                post.getStatus(),
                post.getCreatedAt()
        );
    }

    // 목록 항목의 wantedSlots는 appliedCount가 없다(dto-spec.md §4 PageResponse 예시).
    // 여기서만 쓰여서 중첩 record로 둔다(dto-naming.md §0 규칙 6).
    public record WantedSlotSummary(Instrument instrument, int count) {

        public static WantedSlotSummary from(WantedSlot slot) {
            return new WantedSlotSummary(slot.instrument(), slot.count());
        }
    }
}
