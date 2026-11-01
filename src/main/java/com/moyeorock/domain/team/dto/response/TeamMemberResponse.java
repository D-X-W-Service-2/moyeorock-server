package com.moyeorock.domain.team.dto.response;

import com.moyeorock.domain.team.entity.TeamMember;
import com.moyeorock.domain.team.enums.MemberStatus;
import com.moyeorock.domain.team.enums.TeamRole;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import com.moyeorock.global.common.enums.Instrument;
import java.time.LocalDateTime;

public record TeamMemberResponse(
        UserSummaryResponse user,
        TeamRole role,
        Instrument instrument,
        MemberStatus status,
        LocalDateTime joinedAt
) {

    // 사용자 요약은 user 도메인 Service에서 받아 오므로 엔티티 하나로는 만들 수 없다 → of
    public static TeamMemberResponse of(TeamMember member, UserSummaryResponse user) {
        return new TeamMemberResponse(user, member.getRole(), member.getInstrument(), member.getStatus(),
                member.getJoinedAt());
    }
}
