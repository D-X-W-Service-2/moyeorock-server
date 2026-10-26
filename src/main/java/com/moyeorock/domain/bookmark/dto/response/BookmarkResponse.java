package com.moyeorock.domain.bookmark.dto.response;

import com.moyeorock.domain.bookmark.entity.Bookmark;
import com.moyeorock.domain.bookmark.enums.BookmarkTargetType;
import com.moyeorock.domain.user.dto.response.UserProfileSummaryResponse;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Region;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 저장 항목. {@code target}은 저장한 대상의 요약이고, 대상이 삭제·해체·탈퇴돼 조회되지 않으면 null이다
 * (목록에서 빼지 않는다 — 사용자가 직접 지울 수 있어야 한다). 프로젝트 전역 설정(non_null)상 null이면
 * JSON에서 {@code target} 키 자체가 빠진다.
 */
public record BookmarkResponse(
        Long id,
        BookmarkTargetType targetType,
        Long targetId,
        BookmarkTarget target,
        LocalDateTime createdAt
) {

    public static BookmarkResponse of(Bookmark bookmark, BookmarkTarget target) {
        return new BookmarkResponse(bookmark.getId(), bookmark.getTargetType(), bookmark.getTargetId(),
                target, bookmark.getCreatedAt());
    }

    /** 팀·사용자·곡 세 타입을 한 모양으로 담는다. 해당 없는 필드는 null이다(곡은 region이 없다). */
    public record BookmarkTarget(Long id, String name, Region region, List<Genre> genres, String imageUrl) {

        public static BookmarkTarget fromUser(UserProfileSummaryResponse user) {
            return new BookmarkTarget(user.id(), user.nickname(), user.region(), user.genres(), user.profileImage());
        }
    }
}
