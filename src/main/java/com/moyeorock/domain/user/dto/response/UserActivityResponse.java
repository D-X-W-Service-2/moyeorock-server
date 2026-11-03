package com.moyeorock.domain.user.dto.response;

import com.moyeorock.domain.group.dto.response.GroupSummaryResponse;
import com.moyeorock.domain.performance.enums.PerformanceStatus;
import com.moyeorock.domain.team.dto.response.TeamMembershipResponse;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 내 활동(GET /v1/users/me/activities)과 타인 프로필의 activities(dto-naming §0 예시, dto-spec §1).
 * 세 목록의 재료는 각각 2팀 TeamService·4팀 GroupService·PerformanceService가 주고(ACTIVE 필터도 제공하는 쪽 책임),
 * user는 조립만 한다(이슈 #90). 항목은 제공하는 팀의 응답 DTO를 그대로 담는다(§0 규칙 3 "응답은 최대한 재사용").
 * {@code TeamMembershipResponse}(팀 요약 + 내 역할·악기)와 {@code GroupSummaryResponse}(모임 + 내 역할)가 이미 이 모양이라
 * 중첩 record로 베끼지 않는다. JSON 모양은 §0 예시와 같다.
 *
 * <p>performances 항목만 아직 중첩 record다. 4팀이 이슈 #95에서 {@code PerformanceActivityResponse}(같은 필드)를 만들면 그것으로 바꾼다.
 */
public record UserActivityResponse(
        List<TeamMembershipResponse> teams,
        List<GroupSummaryResponse> groups,
        List<PerformanceActivity> performances
) {

    public record PerformanceActivity(Long id, String title, String groupName,
                                      LocalDateTime performedAt, PerformanceStatus status, String teamName) {}
}
