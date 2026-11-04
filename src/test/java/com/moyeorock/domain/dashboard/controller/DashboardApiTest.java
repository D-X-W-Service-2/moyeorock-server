package com.moyeorock.domain.dashboard.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.notification.entity.Notification;
import com.moyeorock.domain.notification.repository.NotificationRepository;
import com.moyeorock.domain.recruit.dto.request.RecruitPostCreateRequest;
import com.moyeorock.domain.recruit.dto.request.WantedSlotRequest;
import com.moyeorock.domain.recruit.service.RecruitPostService;
import com.moyeorock.domain.rehearsal.dto.request.RehearsalCreateRequest;
import com.moyeorock.domain.rehearsal.service.RehearsalService;
import com.moyeorock.domain.team.dto.request.TeamCreateRequest;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.repository.UserRepository;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.NotificationTargetType;
import com.moyeorock.global.common.enums.NotificationType;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.common.enums.TargetType;
import com.moyeorock.global.security.JwtProvider;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class DashboardApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TeamService teamService;
    @Autowired
    private RehearsalService rehearsalService;
    @Autowired
    private RecruitPostService recruitPostService;
    @Autowired
    private NotificationRepository notificationRepository;

    private long saveUser(String nickname) {
        return userRepository.saveAndFlush(User.signupWithEmail(
                nickname + "@example.com", "hash", nickname, LocalDateTime.now())).getId();
    }

    @Test
    @DisplayName("합주·모집 중 공고·안 읽은 알림 수를 담고 추천곡은 null이다")
    void dashboard() throws Exception {
        long me = saveUser("나");
        long teamId = teamService.create(me,
                new TeamCreateRequest("무명밴드", null, Region.SEOUL, List.of(Genre.ROCK), Instrument.EL_GT)).id();
        LocalDateTime start = LocalDateTime.now().plusHours(2);
        rehearsalService.create(me, teamId, new RehearsalCreateRequest("첫 합주", start, start.plusHours(2), "홍대", null));
        recruitPostService.create(me, new RecruitPostCreateRequest(TargetType.TEAM, teamId, "베이스 구해요", "본문",
                List.of(new WantedSlotRequest(Instrument.BASS, 1)), Region.SEOUL));
        notificationRepository.save(Notification.create(me, NotificationType.TEAM_INVITE, "초대", NotificationTargetType.INVITATION, 1L));
        notificationRepository.save(Notification.create(me, NotificationType.TEAM_INVITE, "초대2", NotificationTargetType.INVITATION, 2L));

        mockMvc.perform(get("/v1/dashboard").header("Authorization", "Bearer " + jwtProvider.generateToken(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upcomingRehearsals", hasSize(1)))
                .andExpect(jsonPath("$.data.upcomingRehearsals[0].title").value("첫 합주"))
                .andExpect(jsonPath("$.data.upcomingRehearsals[0].teamName").value("무명밴드"))
                .andExpect(jsonPath("$.data.openRecruitPosts", hasSize(1)))
                .andExpect(jsonPath("$.data.openRecruitPosts[0].title").value("베이스 구해요"))
                .andExpect(jsonPath("$.data.recommendedSongs").value(nullValue()))
                .andExpect(jsonPath("$.data.unreadNotificationCount").value(2));
    }

    @Test
    @DisplayName("팀도 알림도 없는 새 사용자는 빈 목록과 0이다")
    void dashboard_empty() throws Exception {
        long me = saveUser("새내기");

        mockMvc.perform(get("/v1/dashboard").header("Authorization", "Bearer " + jwtProvider.generateToken(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upcomingRehearsals", hasSize(0)))
                .andExpect(jsonPath("$.data.unreadNotificationCount").value(0));
    }

    @Test
    @DisplayName("토큰이 없으면 401이다")
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/v1/dashboard")).andExpect(status().isUnauthorized());
    }
}
