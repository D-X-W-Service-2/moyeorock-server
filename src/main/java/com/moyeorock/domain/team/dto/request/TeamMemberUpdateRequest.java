package com.moyeorock.domain.team.dto.request;

import com.moyeorock.domain.team.enums.TeamRole;
import com.moyeorock.global.common.enums.Instrument;

// 두 필드 모두 null을 허용하고 보낸 것만 반영한다. role = LEADER면 팀장 위임이다(dto-naming §3).
public record TeamMemberUpdateRequest(TeamRole role, Instrument instrument) {
}
