package com.moyeorock.domain.team.dto.response;

import com.moyeorock.domain.team.entity.TeamMember;
import com.moyeorock.domain.team.enums.TeamRole;
import com.moyeorock.global.common.enums.Instrument;

/**
 * 내가 속한 팀 하나와 그 팀에서의 내 역할·악기. 1팀의 내 활동(UserActivityResponse.teams)이 쓴다.
 * 역할과 악기는 team_members에만 있어서 1팀이 직접 읽을 수 없다(절대 규칙 2).
 */
public record TeamMembershipResponse(TeamSummaryResponse team, TeamRole role, Instrument instrument) {

    // 팀 요약과 팀원 행 두 가지를 합쳐 만든다 → of
    public static TeamMembershipResponse of(TeamSummaryResponse team, TeamMember member) {
        return new TeamMembershipResponse(team, member.getRole(), member.getInstrument());
    }
}
