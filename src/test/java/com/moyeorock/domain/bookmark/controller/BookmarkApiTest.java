package com.moyeorock.domain.bookmark.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.repository.UserRepository;
import com.moyeorock.domain.user.service.UserService;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.security.JwtProvider;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 보안 필터·JSON 직렬화·검증·에러 응답을 실제 HTTP 흐름으로 확인한다. 테스트마다 롤백된다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class BookmarkApiTest {

    private static final String BASE = "/v1/bookmarks";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    private String bearer(long userId) {
        return "Bearer " + jwtProvider.generateToken(userId);
    }

    private User saveUser(String nickname) {
        User user = User.signupWithEmail(nickname + "@example.com", "hash", nickname, LocalDateTime.now());
        user.updateProfile(nickname, Region.GYEONGGI, List.of(Genre.JAZZ), null, "https://img/" + nickname + ".jpg",
                true, true);
        return userRepository.saveAndFlush(user);
    }

    private ResultActions create(long userId, String targetType, long targetId) throws Exception {
        return mockMvc.perform(post(BASE).header("Authorization", bearer(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"" + targetType + "\",\"targetId\":" + targetId + "}"));
    }

    private long createAndGetId(long userId, long targetUserId) throws Exception {
        MvcResult result = create(userId, "USER", targetUserId).andExpect(status().isCreated()).andReturn();
        return ((Number) com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.id"))
                .longValue();
    }

    @Test
    @DisplayName("토큰 없이 호출하면 401이다")
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(BASE + "/1")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("저장 추가: 201과 대상 요약(이름·지역·장르·이미지)을 명세 모양대로 돌려준다")
    void create_returns201WithTarget() throws Exception {
        User target = saveUser("민서");

        create(7L, "USER", target.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.targetType").value("USER"))
                .andExpect(jsonPath("$.data.targetId").value(target.getId()))
                .andExpect(jsonPath("$.data.target.id").value(target.getId()))
                .andExpect(jsonPath("$.data.target.name").value("민서"))
                .andExpect(jsonPath("$.data.target.region").value("GYEONGGI"))
                .andExpect(jsonPath("$.data.target.genres[0]").value("JAZZ"))
                .andExpect(jsonPath("$.data.target.imageUrl").value("https://img/민서.jpg"))
                .andExpect(jsonPath("$.data.createdAt").exists());
    }

    @Test
    @DisplayName("같은 대상을 다시 저장하면 409 BOOKMARK_DUPLICATED다")
    void create_duplicate_returns409() throws Exception {
        User target = saveUser("민서");
        create(7L, "USER", target.getId()).andExpect(status().isCreated());

        create(7L, "USER", target.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BOOKMARK_DUPLICATED"));
    }

    @Test
    @DisplayName("자기 자신도 저장할 수 있다")
    void create_self_isAllowed() throws Exception {
        User me = saveUser("나");

        create(me.getId(), "USER", me.getId()).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("없는 사용자·탈퇴한 사용자를 저장하면 404 USER_NOT_FOUND다")
    void create_missingOrWithdrawnTarget_returns404() throws Exception {
        User withdrawn = saveUser("탈퇴예정");
        userService.withdraw(withdrawn.getId());

        create(7L, "USER", 999_999L).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
        create(7L, "USER", withdrawn.getId()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("임시: TEAM·SONG 저장은 400이고, targetType·targetId 누락·잘못된 값도 400이다")
    void create_unsupportedOrInvalid_returns400() throws Exception {
        create(7L, "TEAM", 5L).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        create(7L, "SONG", 5L).andExpect(status().isBadRequest());
        create(7L, "GROUP", 5L).andExpect(status().isBadRequest());
        mockMvc.perform(post(BASE).header("Authorization", bearer(7L))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetType\":\"USER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("목록: 본인 저장만 최신순으로, 페이지 응답 모양을 따른다")
    void list_ownOnly_latestFirst() throws Exception {
        User a = saveUser("가나");
        User b = saveUser("다라");
        createAndGetId(7L, a.getId());
        createAndGetId(7L, b.getId());
        createAndGetId(8L, a.getId());

        mockMvc.perform(get(BASE).header("Authorization", bearer(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].target.name").value("다라"))
                .andExpect(jsonPath("$.data.content[1].target.name").value("가나"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    @DisplayName("목록: targetType 필터가 적용되고, 잘못된 값은 400, 임의 sort는 500이 아니라 정상 응답이다")
    void list_filterAndParams() throws Exception {
        User a = saveUser("가나");
        createAndGetId(7L, a.getId());

        mockMvc.perform(get(BASE + "?targetType=USER").header("Authorization", bearer(7L)))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get(BASE + "?targetType=TEAM").header("Authorization", bearer(7L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get(BASE + "?targetType=GROUP").header("Authorization", bearer(7L)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + "?sort=bogus,asc").header("Authorization", bearer(7L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("목록: 대상이 탈퇴하면 항목은 남고 target 키만 빠진다 (삭제된 항목으로 표시)")
    void list_withdrawnTarget_omitsTargetKey() throws Exception {
        User target = saveUser("나갈사람");
        createAndGetId(7L, target.getId());
        userService.withdraw(target.getId());

        mockMvc.perform(get(BASE).header("Authorization", bearer(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].targetId").value(target.getId()))
                .andExpect(jsonPath("$.data.content[0].target").doesNotExist());
    }

    @Test
    @DisplayName("삭제: 본인 저장은 200과 {id, deleted:true}를 돌려주고 목록에서 사라진다")
    void delete_own() throws Exception {
        User target = saveUser("민서");
        long id = createAndGetId(7L, target.getId());

        mockMvc.perform(delete(BASE + "/" + id).header("Authorization", bearer(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.deleted").value(true));
        mockMvc.perform(get(BASE).header("Authorization", bearer(7L)))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("삭제: 남의 저장·없는 저장은 똑같이 404 BOOKMARK_NOT_FOUND다")
    void delete_othersOrMissing_returns404() throws Exception {
        User target = saveUser("민서");
        long id = createAndGetId(7L, target.getId());

        mockMvc.perform(delete(BASE + "/" + id).header("Authorization", bearer(8L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BOOKMARK_NOT_FOUND"));
        mockMvc.perform(delete(BASE + "/999999").header("Authorization", bearer(7L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BOOKMARK_NOT_FOUND"));
    }
}
