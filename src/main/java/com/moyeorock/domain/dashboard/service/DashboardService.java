package com.moyeorock.domain.dashboard.service;

import com.moyeorock.domain.dashboard.dto.response.DashboardResponse;
import com.moyeorock.domain.notification.service.NotificationService;
import com.moyeorock.domain.recruit.dto.request.RecruitPostSearchCondition;
import com.moyeorock.domain.recruit.dto.response.RecruitPostSummaryResponse;
import com.moyeorock.domain.recruit.enums.RecruitStatus;
import com.moyeorock.domain.recruit.service.RecruitPostService;
import com.moyeorock.domain.rehearsal.dto.response.RehearsalSummaryResponse;
import com.moyeorock.domain.rehearsal.service.RehearsalService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 대시보드(GET /v1/dashboard). 2팀 합주·3팀 공고·알림 Service를 불러 한 응답으로 모으기만 한다.
 * 다른 도메인 Service 호출이 섞여 있고 항목끼리 같은 시점일 필요가 없어 트랜잭션을 열지 않는다(conventions.md §3).
 * 다른 도메인은 이 클래스를 주입받지 않는다.
 *
 * <p>항목 기준: 합주는 오늘부터 {@value #REHEARSAL_DAYS}일 안에 끝나지 않은 것을 가까운 순 {@value #LIMIT}개,
 * 공고는 모집 중인 것을 최신순 {@value #LIMIT}개(개인화하지 않는다). 추천곡은 AI 연결 전까지 null이다.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int REHEARSAL_DAYS = 14;
    private static final int LIMIT = 5;

    private final RehearsalService rehearsalService;
    private final RecruitPostService recruitPostService;
    private final NotificationService notificationService;

    public DashboardResponse getDashboard(Long userId) {
        return new DashboardResponse(
                upcomingRehearsals(userId),
                openRecruitPosts(),
                null,
                notificationService.countUnread(userId));
    }

    // 합주 조회 기간은 날짜 단위라 오늘 이미 끝난 합주가 섞여 온다 → 끝나지 않은 것만 남긴다.
    private List<RehearsalSummaryResponse> upcomingRehearsals(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        return rehearsalService.getMyRehearsals(userId, today, today.plusDays(REHEARSAL_DAYS - 1L)).rehearsals()
                .stream()
                .filter(rehearsal -> !(rehearsal.endsAt() != null ? rehearsal.endsAt() : rehearsal.startsAt()).isBefore(now))
                .limit(LIMIT)
                .toList();
    }

    private List<RecruitPostSummaryResponse> openRecruitPosts() {
        return recruitPostService.search(new RecruitPostSearchCondition(null, null, null, RecruitStatus.OPEN),
                PageRequest.of(0, LIMIT)).content();
    }
}
