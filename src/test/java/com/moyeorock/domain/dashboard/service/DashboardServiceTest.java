package com.moyeorock.domain.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.moyeorock.domain.dashboard.dto.response.DashboardResponse;
import com.moyeorock.domain.notification.service.NotificationService;
import com.moyeorock.domain.recruit.dto.request.RecruitPostSearchCondition;
import com.moyeorock.domain.recruit.dto.response.RecruitPostSummaryResponse;
import com.moyeorock.domain.recruit.enums.RecruitStatus;
import com.moyeorock.domain.recruit.service.RecruitPostService;
import com.moyeorock.domain.rehearsal.dto.response.RehearsalSummaryResponse;
import com.moyeorock.domain.rehearsal.dto.response.RehearsalsResponse;
import com.moyeorock.domain.rehearsal.service.RehearsalService;
import com.moyeorock.global.common.dto.PageResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final Long ME = 1L;

    @Mock
    private RehearsalService rehearsalService;
    @Mock
    private RecruitPostService recruitPostService;
    @Mock
    private NotificationService notificationService;
    @InjectMocks
    private DashboardService dashboardService;

    private static RehearsalSummaryResponse rehearsal(long id, LocalDateTime startsAt, LocalDateTime endsAt) {
        return new RehearsalSummaryResponse(id, 10L, "무명밴드", "합주" + id, startsAt, endsAt, "홍대");
    }

    private void givenEmptyOthers() {
        given(recruitPostService.search(any(), any())).willReturn(PageResponse.from(new PageImpl<RecruitPostSummaryResponse>(
                List.of(), PageRequest.of(0, 5), 0)));
        given(notificationService.countUnread(ME)).willReturn(3);
    }

    @Test
    @DisplayName("추천곡은 null이고, 안 읽은 알림 수와 모집 중 공고 5개 조회를 담는다")
    void recommendedSongsNull_unreadAndOpenPosts() {
        givenEmptyOthers();
        given(rehearsalService.getMyRehearsals(eq(ME), any(), any())).willReturn(new RehearsalsResponse(List.of()));

        DashboardResponse response = dashboardService.getDashboard(ME);

        assertThat(response.recommendedSongs()).isNull();
        assertThat(response.unreadNotificationCount()).isEqualTo(3);
        ArgumentCaptor<RecruitPostSearchCondition> condition = ArgumentCaptor.forClass(RecruitPostSearchCondition.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(recruitPostService).search(condition.capture(), pageable.capture());
        assertThat(condition.getValue().status()).isEqualTo(RecruitStatus.OPEN);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("합주는 오늘부터 14일 기간으로 조회하고, 이미 끝난 합주는 빼며, 5개까지만 남긴다")
    void rehearsals_excludesFinished_andLimitsToFive() {
        givenEmptyOthers();
        LocalDateTime now = LocalDateTime.now();
        java.util.ArrayList<RehearsalSummaryResponse> all = new java.util.ArrayList<>();
        all.add(rehearsal(1, now.minusHours(3), now.minusHours(1)));
        for (int i = 2; i <= 8; i++) {
            all.add(rehearsal(i, now.plusHours(i), now.plusHours(i + 1)));
        }
        given(rehearsalService.getMyRehearsals(eq(ME), any(), any())).willReturn(new RehearsalsResponse(all));

        DashboardResponse response = dashboardService.getDashboard(ME);

        assertThat(response.upcomingRehearsals()).extracting(RehearsalSummaryResponse::id)
                .containsExactly(2L, 3L, 4L, 5L, 6L);
        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        org.mockito.Mockito.verify(rehearsalService).getMyRehearsals(eq(ME), from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(LocalDate.now());
        assertThat(to.getValue()).isEqualTo(LocalDate.now().plusDays(13));
    }

    @Test
    @DisplayName("지금 진행 중인 합주는 남긴다")
    void rehearsals_keepsOngoing() {
        givenEmptyOthers();
        LocalDateTime now = LocalDateTime.now();
        given(rehearsalService.getMyRehearsals(eq(ME), any(), any())).willReturn(new RehearsalsResponse(
                List.of(rehearsal(1, now.minusMinutes(30), now.plusMinutes(30)))));

        assertThat(dashboardService.getDashboard(ME).upcomingRehearsals()).hasSize(1);
    }
}
