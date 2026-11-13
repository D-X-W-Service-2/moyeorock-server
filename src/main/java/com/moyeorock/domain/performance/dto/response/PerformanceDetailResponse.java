package com.moyeorock.domain.performance.dto.response;

import com.moyeorock.domain.group.dto.response.GroupBriefResponse;
import com.moyeorock.domain.performance.entity.Performance;
import com.moyeorock.domain.performance.enums.PerformanceStatus;
import com.moyeorock.domain.setlist.dto.response.SetlistItemResponse;
import com.moyeorock.domain.team.dto.response.TeamSummaryResponse;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 공연 생성·상세·수정 공통 응답(dto-naming.md 지켜야 할 6가지 #3 — 생성·수정은 상세 DTO를 재사용).
 *
 * <p>{@code teams}는 해체되지 않은 참여 팀이라 끝난 공연(팀이 모두 해체됨)은 빈 목록이다. {@code myTeamIds}는 요청자가
 * 속한 이 공연의 팀 id 목록이다 — 한 사람이 여러 팀에 속할 수 있고, 없으면 빈 목록이다.
 * {@code setlist}는 확정된 셋리스트만 순서대로 담는다. 확정 전이면 빈 목록이고({@code null}은 보내지 않는다), 끝난 공연도
 * 기록이라 그대로 보인다. <b>빈 목록이 곧 확정 전은 아니다</b> — 확정 뒤 팀이 개별 해체되면 그 팀의 셋리스트 행이 지워져
 * (architecture.md §6 예외) 확정곡을 넘긴 팀이 모두 해체되면 확정 상태여도 빈 목록이고, 일부만 해체되면 {@code sortOrder}가
 * 1, 3처럼 빈다. 확정 여부는 셋리스트 조회({@code SetlistResponse.confirmed})로 보고, 순서는 배열 순서를 쓴다(PR #146 리뷰).
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
        List<SetlistItemResponse> setlist,
        UserSummaryResponse createdBy,
        boolean canEdit,
        List<Long> myTeamIds,
        LocalDateTime createdAt
) {

    public static PerformanceDetailResponse of(Performance performance, GroupBriefResponse group,
            List<TeamSummaryResponse> teams, List<SetlistItemResponse> setlist, UserSummaryResponse createdBy,
            boolean canEdit, List<Long> myTeamIds) {
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
                setlist,
                createdBy,
                canEdit,
                myTeamIds,
                performance.getCreatedAt()
        );
    }
}
