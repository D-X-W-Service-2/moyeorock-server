package com.moyeorock.domain.rehearsal.dto.response;

import com.moyeorock.domain.rehearsal.entity.Rehearsal;
import java.time.LocalDateTime;

// 목록의 한 줄. 내 합주 일정에서는 여러 팀의 합주가 섞여 나오므로 teamId·teamName을 함께 담는다.
// 3팀 dashboard의 upcomingRehearsals도 같은 필드를 쓴다 — 바꿀 때는 3팀과 공유한다.
public record RehearsalSummaryResponse(
        Long id,
        Long teamId,
        String teamName,
        String title,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String place
) {

    // 합주와 팀 이름 두 가지를 합쳐 만든다 → of (팀 이름은 team 도메인에서 받아온다)
    public static RehearsalSummaryResponse of(Rehearsal rehearsal, String teamName) {
        return new RehearsalSummaryResponse(rehearsal.getId(), rehearsal.getTeamId(), teamName,
                rehearsal.getTitle(), rehearsal.getStartsAt(), rehearsal.getEndsAt(), rehearsal.getPlace());
    }
}
