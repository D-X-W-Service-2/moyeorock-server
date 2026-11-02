package com.moyeorock.domain.notice.dto.response;

import com.moyeorock.domain.notice.entity.GroupNotice;
import java.time.LocalDateTime;

/**
 * 공지 목록 항목. 본문({@code body})을 담지 않는다 — 전체 내용은 공지 상세에서 조회한다
 * (목록·상세 분리, dto-naming.md §8). 작성자도 담지 않는다(권한이 현재 모임장 고정).
 */
public record NoticeSummaryResponse(
        Long id,
        Long groupId,
        String title,
        boolean isPinned,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static NoticeSummaryResponse from(GroupNotice notice) {
        return new NoticeSummaryResponse(
                notice.getId(),
                notice.getGroupId(),
                notice.getTitle(),
                notice.isPinned(),
                notice.getCreatedAt(),
                notice.getUpdatedAt()
        );
    }
}
