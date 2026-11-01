package com.moyeorock.domain.team.dto.response;

import com.moyeorock.domain.team.entity.TeamMember;
import com.moyeorock.domain.team.enums.MemberStatus;

// 탈퇴는 LEFT, 강퇴는 REMOVED로 내려간다.
public record TeamMemberStatusResponse(Long teamId, Long userId, MemberStatus status) {

    public static TeamMemberStatusResponse from(TeamMember member) {
        return new TeamMemberStatusResponse(member.getTeamId(), member.getUserId(), member.getStatus());
    }
}
