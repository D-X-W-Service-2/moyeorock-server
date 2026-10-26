package com.moyeorock.domain.notification.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.notification.entity.Notification;
import com.moyeorock.domain.notification.repository.NotificationRepository;
import com.moyeorock.global.common.enums.NotificationTargetType;
import com.moyeorock.global.common.enums.NotificationType;
import com.moyeorock.global.security.JwtProvider;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 보안 필터·JSON 직렬화(isRead 키)·바인딩·검증·에러 응답을 실제 HTTP 흐름으로 확인한다. 테스트마다 롤백된다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class NotificationApiTest {

    private static final String BASE = "/v1/notifications";
    private static final long ME = 7L;
    private static final long OTHER = 8L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private NotificationRepository notificationRepository;

    private String bearer(long userId) {
        return "Bearer " + jwtProvider.generateToken(userId);
    }

    private Notification save(long userId, String message) {
        return notificationRepository.saveAndFlush(Notification.create(
                userId, NotificationType.TEAM_INVITE, message, NotificationTargetType.INVITATION, 91L));
    }

    private ResultActions read(long userId, String body) throws Exception {
        var request = patch(BASE + "/read").header("Authorization", bearer(userId));
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    @Test
    @DisplayName("토큰 없이 호출하면 401이다")
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(patch(BASE + "/read")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("목록: unreadCount와 중첩된 notifications 페이지, 항목 필드(isRead 키 포함)를 명세 모양대로 돌려준다")
    void list_matchesSpecShape() throws Exception {
        save(ME, "첫째");
        save(ME, "둘째");
        save(OTHER, "남의 알림");

        mockMvc.perform(get(BASE).header("Authorization", bearer(ME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(2))
                .andExpect(jsonPath("$.data.notifications.content.length()").value(2))
                .andExpect(jsonPath("$.data.notifications.content[0].message").value("둘째"))
                .andExpect(jsonPath("$.data.notifications.content[0].type").value("TEAM_INVITE"))
                .andExpect(jsonPath("$.data.notifications.content[0].targetType").value("INVITATION"))
                .andExpect(jsonPath("$.data.notifications.content[0].targetId").value(91))
                .andExpect(jsonPath("$.data.notifications.content[0].isRead").value(false))
                .andExpect(jsonPath("$.data.notifications.content[0].createdAt").exists())
                .andExpect(jsonPath("$.data.notifications.page").value(0))
                .andExpect(jsonPath("$.data.notifications.size").value(20))
                .andExpect(jsonPath("$.data.notifications.totalElements").value(2))
                .andExpect(jsonPath("$.data.notifications.totalPages").value(1))
                .andExpect(jsonPath("$.data.notifications.last").value(true));
    }

    @Test
    @DisplayName("목록: 임의 sort 파라미터가 와도 500이 아니라 최신순으로 정상 응답한다")
    void list_withArbitrarySort_stillOk() throws Exception {
        save(ME, "첫째");
        save(ME, "둘째");

        mockMvc.perform(get(BASE + "?sort=bogus,asc").header("Authorization", bearer(ME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications.content[0].message").value("둘째"));
    }

    @Test
    @DisplayName("지정 읽음: 지정한 본인 알림만 읽음 처리하고, 남의 알림 id는 건수에 안 잡힌다")
    void read_selected_ignoresOthers() throws Exception {
        Notification mine = save(ME, "내 알림");
        save(ME, "안 고른 알림");
        Notification others = save(OTHER, "남의 알림");

        read(ME, "{\"ids\":[" + mine.getId() + "," + others.getId() + "]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readCount").value(1))
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        mockMvc.perform(get(BASE).header("Authorization", bearer(OTHER)))
                .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test
    @DisplayName("전체 읽음: ids가 빈 배열·null·누락이거나 바디가 없어도 본인 안읽은 알림을 모두 읽음 처리한다")
    void read_all_variants() throws Exception {
        save(ME, "1");
        save(ME, "2");
        save(OTHER, "남의 알림");

        read(ME, "{\"ids\":[]}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readCount").value(2))
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        save(ME, "3");
        read(ME, "{\"ids\":null}").andExpect(jsonPath("$.data.readCount").value(1));

        save(ME, "4");
        read(ME, "{}").andExpect(jsonPath("$.data.readCount").value(1));

        save(ME, "5");
        read(ME, null).andExpect(status().isOk()).andExpect(jsonPath("$.data.readCount").value(1));

        mockMvc.perform(get(BASE).header("Authorization", bearer(OTHER)))
                .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test
    @DisplayName("ids가 100개를 넘거나 null 원소가 있으면 400 VALIDATION_FAILED다")
    void read_invalidIds_returns400() throws Exception {
        String tooMany = LongStream.rangeClosed(1, 101).mapToObj(String::valueOf).collect(Collectors.joining(","));

        read(ME, "{\"ids\":[" + tooMany + "]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        read(ME, "{\"ids\":[1,null]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        String exactly100 = LongStream.rangeClosed(1, 100).mapToObj(String::valueOf).collect(Collectors.joining(","));
        read(ME, "{\"ids\":[" + exactly100 + "]}").andExpect(status().isOk());
    }
}
