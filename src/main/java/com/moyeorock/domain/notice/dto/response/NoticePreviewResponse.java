package com.moyeorock.domain.notice.dto.response;

import com.moyeorock.domain.notice.entity.GroupNotice;
import java.time.LocalDateTime;

/**
 * 동아리 홈(모임 상세)의 고정 공지 미리보기 항목. group 도메인이 이 응답 DTO를 가져다 쓴다
 * (conventions.md §6 — 다른 도메인에서 import 가능한 것은 Service와 응답 DTO).
 * 목록용 {@link NoticeSummaryResponse}와 달리 홈 화면에 필요한 4개 필드만 담는다.
 */
public record NoticePreviewResponse(Long id, String title, boolean isPinned, LocalDateTime createdAt) {

    public static NoticePreviewResponse from(GroupNotice notice) {
        return new NoticePreviewResponse(
                notice.getId(), notice.getTitle(), notice.isPinned(), notice.getCreatedAt());
    }
}
