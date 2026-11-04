package com.moyeorock.domain.dashboard.dto.response;

import com.moyeorock.domain.recruit.dto.response.RecruitPostSummaryResponse;
import com.moyeorock.domain.rehearsal.dto.response.RehearsalSummaryResponse;
import com.moyeorock.domain.song.dto.response.SongSummaryResponse;
import java.util.List;

/**
 * 대시보드 통합 응답. 네 항목 모두 다른 도메인이 주는 값을 그대로 담는다.
 * {@code recommendedSongs}는 AI 추천이 연결되기 전까지 {@code null}이다(JSON에서도 {@code null}로 내려간다).
 */
public record DashboardResponse(
        List<RehearsalSummaryResponse> upcomingRehearsals,
        List<RecruitPostSummaryResponse> openRecruitPosts,
        List<SongSummaryResponse> recommendedSongs,
        int unreadNotificationCount
) {
}
