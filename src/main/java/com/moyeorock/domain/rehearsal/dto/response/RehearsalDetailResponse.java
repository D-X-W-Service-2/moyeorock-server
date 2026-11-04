package com.moyeorock.domain.rehearsal.dto.response;

import com.moyeorock.domain.rehearsal.entity.Rehearsal;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import java.time.LocalDateTime;

// 상세·생성·수정 응답이 함께 쓴다(dto-naming.md §0 규칙 3). 요약에 memo·createdBy·canEdit이 더해진 모양이다.
public record RehearsalDetailResponse(
        Long id,
        TeamInfo team,
        String title,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String place,
        String memo,
        UserSummaryResponse createdBy,
        boolean canEdit,
        LocalDateTime createdAt
) {

    // 이 응답에서만 쓰는 항목이라 중첩 record로 둔다(dto-naming.md §0 규칙 6)
    public record TeamInfo(Long id, String name) {
    }

    // canEdit은 작성자이거나 팀장일 때 true다. 판단은 Service가 하고 DTO는 받은 값을 담기만 한다.
    // 탈퇴한 작성자는 createdBy가 null이 될 수 있다.
    public static RehearsalDetailResponse of(Rehearsal rehearsal, String teamName, UserSummaryResponse createdBy,
            boolean canEdit) {
        return new RehearsalDetailResponse(rehearsal.getId(), new TeamInfo(rehearsal.getTeamId(), teamName),
                rehearsal.getTitle(), rehearsal.getStartsAt(), rehearsal.getEndsAt(), rehearsal.getPlace(),
                rehearsal.getMemo(), createdBy, canEdit, rehearsal.getCreatedAt());
    }
}
