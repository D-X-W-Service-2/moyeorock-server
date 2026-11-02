package com.moyeorock.domain.notice.dto.response;

import com.moyeorock.domain.notice.entity.GroupNotice;
import java.time.LocalDateTime;

/**
 * 공지 상세, 그리고 작성·수정 응답(dto-naming.md 지켜야 할 6가지 #3 — 생성·수정은 상세 DTO를 재사용).
 * 작성자({@code author})를 담지 않는다 — 작성·수정·삭제 권한이 현재 모임장으로 고정이라
 * 화면에서 작성자를 구분할 이유가 없다. {@code group_notices.author_id}는 이력용으로만 저장한다.
 */
public record NoticeDetailResponse(
        Long id,
        Long groupId,
        String title,
        String body,
        boolean isPinned,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static NoticeDetailResponse from(GroupNotice notice) {
        return new NoticeDetailResponse(
                notice.getId(),
                notice.getGroupId(),
                notice.getTitle(),
                notice.getBody(),
                notice.isPinned(),
                notice.getCreatedAt(),
                notice.getUpdatedAt()
        );
    }
}
