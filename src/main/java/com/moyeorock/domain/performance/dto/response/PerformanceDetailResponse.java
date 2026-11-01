package com.moyeorock.domain.performance.dto.response;

import com.moyeorock.domain.group.dto.response.GroupBriefResponse;
import com.moyeorock.domain.performance.entity.Performance;
import com.moyeorock.domain.performance.enums.PerformanceStatus;
import com.moyeorock.domain.team.dto.response.TeamSummaryResponse;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 공연 생성·상세·수정 공통 응답(dto-naming.md 지켜야 할 6가지 #3 — 생성·수정은 상세 DTO를 재사용).
 *
 * <p>{@code teams}는 해체되지 않은 참여 팀이라 끝난 공연(팀이 모두 해체됨)은 빈 목록이다. {@code myTeamIds}는 요청자가
 * 속한 이 공연의 팀 id 목록이다 — 한 사람이 여러 팀에 속할 수 있고, 없으면 빈 목록이다.
 * 명세(Notion API 초안)의 {@code setlist}는 아직 담지 않는다 — 셋리스트 도메인 구현 때 채운다.
 * {@code createdBy}는 만든 사람이 탈퇴했으면 {@code null}이다.
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
        List<TeamSummaryResponse> teams,
        UserSummaryResponse createdBy,
        boolean canEdit,
        List<Long> myTeamIds,
        LocalDateTime createdAt
) {

    public static PerformanceDetailResponse of(Performance performance, GroupBriefResponse group,
            List<TeamSummaryResponse> teams, UserSummaryResponse createdBy, boolean canEdit, List<Long> myTeamIds) {
        return new PerformanceDetailResponse(
                performance.getId(),
                group,
                performance.getTitle(),
                performance.getDescription(),
                performance.getPerformedAt(),
                performance.getVenue(),
                performance.getPosterImage(),
                performance.getStatus(),
                teams,
                createdBy,
                canEdit,
                myTeamIds,
                performance.getCreatedAt()
        );
    }
}
