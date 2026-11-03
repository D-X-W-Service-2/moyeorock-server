package com.moyeorock.domain.group.dto.response;

import com.moyeorock.domain.group.entity.Group;
import com.moyeorock.domain.group.enums.GroupRole;
import com.moyeorock.domain.group.enums.GroupType;
import com.moyeorock.domain.notice.dto.response.NoticePreviewResponse;
import com.moyeorock.domain.performance.dto.response.PerformanceSummaryResponse;
import com.moyeorock.global.common.enums.Region;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 모임 생성·상세·수정 공통 응답 (동아리 홈 화면).
 *
 * <p>명세(Notion API 초안)의 {@code owner}(UserSummary)는 아직 담지 않는다 — 지금은 모임장 식별자만
 * {@code ownerId}로 내려주고 user 도메인 연동 때 채운다.
 *
 * <p>{@code upcomingPerformances}는 다가오는 공연 최대 3건이고 없으면 빈 배열이다(필드는 항상 내린다, #112).
 * 항목은 공연 목록과 같은 {@link PerformanceSummaryResponse}다.
 */
public record GroupDetailResponse(
        Long id,
        String name,
        String description,
        GroupType type,
        Region region,
        String coverImage,
        Long ownerId,
        int memberCount,
        GroupRole myRole,
        List<NoticePreviewResponse> pinnedNotices,
        List<PerformanceSummaryResponse> upcomingPerformances,
        LocalDateTime createdAt
) {

    public static GroupDetailResponse of(Group group, Long ownerId, int memberCount, GroupRole myRole,
            List<NoticePreviewResponse> pinnedNotices, List<PerformanceSummaryResponse> upcomingPerformances) {
        return new GroupDetailResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getType(),
                group.getRegion(),
                group.getCoverImage(),
                ownerId,
                memberCount,
                myRole,
                pinnedNotices,
                upcomingPerformances,
                group.getCreatedAt()
        );
    }
}
