package com.moyeorock.domain.user.dto.response;

import com.moyeorock.domain.group.enums.GroupRole;
import com.moyeorock.domain.group.enums.GroupType;
import com.moyeorock.domain.performance.enums.PerformanceStatus;
import com.moyeorock.domain.team.dto.response.TeamSummaryResponse;
import com.moyeorock.domain.team.enums.TeamRole;
import com.moyeorock.global.common.enums.Instrument;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 내 활동(GET /v1/users/me/activities)과 타인 프로필의 activities. dto-naming §0 예시 코드 그대로다.
 * 세 목록의 재료는 각각 2팀 TeamService·4팀 GroupService·PerformanceService가 주고(ACTIVE 필터도 제공하는 쪽 책임),
 * user는 응답으로 옮기기만 한다(이슈 #90). role·instrument·teamName 같은 사용자 관점 값이 있어 각 팀 요약 DTO를 그대로 쓰지 않고 중첩 record로 둔다.
 * enum은 각 도메인 것이다(공용 아님, domains.md "값이 다르면 공유 금지").
 */
public record UserActivityResponse(
        List<TeamActivity> teams,
        List<GroupActivity> groups,
        List<PerformanceActivity> performances
) {
    public record TeamActivity(TeamSummaryResponse team, TeamRole role, Instrument instrument) {}

    public record GroupActivity(Long id, String name, GroupType type, GroupRole role, String coverImage) {}

    public record PerformanceActivity(Long id, String title, String groupName,
                                      LocalDateTime performedAt, PerformanceStatus status, String teamName) {}
}
