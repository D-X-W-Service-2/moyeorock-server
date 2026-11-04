package com.moyeorock.domain.team.dto.response;

import com.moyeorock.domain.performance.dto.response.PerformanceBriefResponse;
import com.moyeorock.domain.setlist.dto.response.TeamSongResponse;
import com.moyeorock.domain.team.entity.Team;
import com.moyeorock.domain.team.enums.TeamRole;
import com.moyeorock.domain.team.enums.TeamStatus;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Region;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 팀 상세. songs는 팀이 고른 선택곡이고 없으면 빈 목록이다.
 * performance는 공연 팀이면 소속 공연 요약, 독립 팀이면 null이다. myRole은 요청자가 팀원이 아니면 null이다.
 */
public record TeamDetailResponse(
        Long id,
        String name,
        String description,
        Region region,
        List<Genre> genres,
        TeamStatus status,
        PerformanceBriefResponse performance,
        UserSummaryResponse leader,
        int memberCount,
        List<TeamMemberResponse> members,
        List<TeamSongResponse> songs,
        TeamRole myRole,
        LocalDateTime createdAt
) {

    // 팀·장르·소속 공연·팀장·팀원·선택곡·내 역할을 합쳐 만든다. memberCount는 ACTIVE 팀원 목록의 크기다 → of
    public static TeamDetailResponse of(Team team, List<Genre> genres, PerformanceBriefResponse performance,
            UserSummaryResponse leader, List<TeamMemberResponse> members, List<TeamSongResponse> songs,
            TeamRole myRole) {
        return new TeamDetailResponse(team.getId(), team.getName(), team.getDescription(), team.getRegion(),
                genres, team.getStatus(), performance, leader, members.size(), members, songs, myRole,
                team.getCreatedAt());
    }
}
