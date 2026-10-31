package com.moyeorock.domain.team.dto.response;

import com.moyeorock.domain.team.entity.Team;
import com.moyeorock.domain.team.enums.TeamRole;
import com.moyeorock.domain.team.enums.TeamStatus;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Region;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 팀 상세. 명세(dto-spec §2)에는 소속 공연(performance)과 셋리스트(setlist)도 있지만 둘 다 4팀 소유 DTO가
 * 필요한데 아직 없어서 뺐다. 4팀 DTO가 생기면 필드를 추가한다(결정 2).
 * myRole은 요청자가 팀원이 아니면 null이다.
 */
public record TeamDetailResponse(
        Long id,
        String name,
        String description,
        Region region,
        List<Genre> genres,
        TeamStatus status,
        UserSummaryResponse leader,
        int memberCount,
        List<TeamMemberResponse> members,
        TeamRole myRole,
        LocalDateTime createdAt
) {

    // 팀·장르·팀장·팀원·내 역할을 합쳐 만든다. memberCount는 ACTIVE 팀원 목록의 크기다 → of
    public static TeamDetailResponse of(Team team, List<Genre> genres, UserSummaryResponse leader,
            List<TeamMemberResponse> members, TeamRole myRole) {
        return new TeamDetailResponse(team.getId(), team.getName(), team.getDescription(), team.getRegion(),
                genres, team.getStatus(), leader, members.size(), members, myRole, team.getCreatedAt());
    }
}
