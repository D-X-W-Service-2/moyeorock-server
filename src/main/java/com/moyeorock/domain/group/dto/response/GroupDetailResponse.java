package com.moyeorock.domain.group.dto.response;

import com.moyeorock.domain.group.entity.Group;
import com.moyeorock.domain.group.enums.GroupRole;
import com.moyeorock.domain.group.enums.GroupType;
import com.moyeorock.domain.notice.dto.response.NoticePreviewResponse;
import com.moyeorock.global.common.enums.Region;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 모임 생성·상세·수정 공통 응답 (동아리 홈 화면).
 *
 * <p>명세(Notion API 초안)의 {@code owner}(UserSummary)와 {@code upcomingPerformances}는 아직 담지 않는다.
 * 전자는 user 도메인 Service(PR #57 미머지), 후자는 performance 도메인이 있어야 채울 수 있다.
 * 지금은 모임장의 식별자만 {@code ownerId}로 내려주고, 두 도메인이 준비되면 교체·추가한다.
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
        LocalDateTime createdAt
) {

    public static GroupDetailResponse of(Group group, Long ownerId, int memberCount, GroupRole myRole,
            List<NoticePreviewResponse> pinnedNotices) {
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
                group.getCreatedAt()
        );
    }
}
