package com.moyeorock.domain.performance.dto.response;

import com.moyeorock.domain.group.dto.response.GroupBriefResponse;
import com.moyeorock.domain.performance.entity.Performance;
import com.moyeorock.domain.performance.enums.PerformanceStatus;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import java.time.LocalDateTime;

/**
 * 공연 생성·상세·수정 공통 응답(dto-naming.md 지켜야 할 6가지 #3 — 생성·수정은 상세 DTO를 재사용).
 *
 * <p>명세(Notion API 초안)의 {@code teams}·{@code myTeamIds}·{@code setlist}는 아직 담지 않는다 —
 * {@code teams}·{@code myTeamIds}는 2팀 TeamService(#79)가 생긴 뒤 공연 상태 변경·공연 내 팀 생성과 함께,
 * {@code setlist}는 셋리스트 도메인 구현 때 채운다. {@code createdBy}는 만든 사람이 탈퇴했으면 {@code null}이다.
 */
public record PerformanceDetailResponse(
        Long id,
        GroupBriefResponse group,
        String title,
        String description,
        LocalDateTime performedAt,
        String venue,
        String posterImage,
        PerformanceStatus status,
        UserSummaryResponse createdBy,
        boolean canEdit,
        LocalDateTime createdAt
) {

    public static PerformanceDetailResponse of(Performance performance, GroupBriefResponse group,
            UserSummaryResponse createdBy, boolean canEdit) {
        return new PerformanceDetailResponse(
                performance.getId(),
                group,
                performance.getTitle(),
                performance.getDescription(),
                performance.getPerformedAt(),
                performance.getVenue(),
                performance.getPosterImage(),
                performance.getStatus(),
                createdBy,
                canEdit,
                performance.getCreatedAt()
        );
    }
}
