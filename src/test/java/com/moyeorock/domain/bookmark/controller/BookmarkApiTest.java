package com.moyeorock.domain.bookmark.controller;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.team.dto.request.TeamCreateRequest;
import com.moyeorock.domain.team.dto.request.TeamStatusUpdateRequest;
import com.moyeorock.domain.team.enums.TeamStatus;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.repository.UserRepository;
import com.moyeorock.domain.user.service.UserWithdrawService;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
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
    // 저장 대상으로 만드는 사용자의 자동 증가 id와 겹쳐 "자기 자신 저장"이 되지 않도록 큰 값을 쓴다
    private static final long OWNER = 7_000_001L;
    private static final long OTHER_OWNER = 8_000_001L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserWithdrawService userWithdrawService;

    @Autowired
    private TeamService teamService;

    private long createTeam(long leaderId, String name) {
        return teamService.create(leaderId,
                new TeamCreateRequest(name, null, Region.SEOUL, List.of(Genre.ROCK), Instrument.BASS)).id();
    }

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

        create(OWNER, "USER", target.getId())
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
        create(OWNER, "USER", target.getId()).andExpect(status().isCreated());

        create(OWNER, "USER", target.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKMARK_DUPLICATED"));
    }

    @Test
    @DisplayName("자기 자신은 저장할 수 없다 — 400 VALIDATION_FAILED")
    void create_self_returns400() throws Exception {
        User me = saveUser("나");

        create(me.getId(), "USER", me.getId()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("없는 사용자·탈퇴한 사용자를 저장하면 404 USER_NOT_FOUND다")
    void create_missingOrWithdrawnTarget_returns404() throws Exception {
        User withdrawn = saveUser("탈퇴예정");
        userWithdrawService.withdraw(withdrawn.getId());

        create(OWNER, "USER", 999_999L).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        create(OWNER, "USER", withdrawn.getId()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("저장 추가: TEAM 대상은 201과 팀 요약(이름·지역·장르)을 돌려주고 imageUrl은 null이다")
    void create_teamTarget_returns201() throws Exception {
        long teamId = createTeam(saveUser("팀장").getId(), "무명밴드");

        create(OWNER, "TEAM", teamId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.targetType").value("TEAM"))
                .andExpect(jsonPath("$.data.targetId").value(teamId))
                .andExpect(jsonPath("$.data.target.name").value("무명밴드"))
                .andExpect(jsonPath("$.data.target.region").value("SEOUL"))
                .andExpect(jsonPath("$.data.target.genres[0]").value("ROCK"))
                .andExpect(jsonPath("$.data.target.imageUrl").value(nullValue()));
    }

    @Test
    @DisplayName("TEAM 저장: 같은 팀을 다시 저장하면 409, 없는 팀·해체된 팀은 404 TEAM_NOT_FOUND다")
    void create_teamTarget_duplicateAndMissing() throws Exception {
        long leaderId = saveUser("팀장").getId();
        long teamId = createTeam(leaderId, "무명밴드");
        create(OWNER, "TEAM", teamId).andExpect(status().isCreated());
        create(OWNER, "TEAM", teamId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKMARK_DUPLICATED"));

        create(OWNER, "TEAM", 999_999L).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
        long disbandedId = createTeam(leaderId, "해체밴드");
        teamService.disband(leaderId, disbandedId, new TeamStatusUpdateRequest(TeamStatus.DISBANDED));
        create(OWNER, "TEAM", disbandedId).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("목록: 저장한 팀이 해체되면 항목은 남고 target이 null이며, targetType=TEAM 필터가 팀만 거른다")
    void list_teamTarget_andDisbanded() throws Exception {
        long leaderId = saveUser("팀장").getId();
        long aliveId = createTeam(leaderId, "살아있는밴드");
        long disbandedId = createTeam(leaderId, "해체될밴드");
        create(OWNER, "TEAM", aliveId).andExpect(status().isCreated());
        create(OWNER, "TEAM", disbandedId).andExpect(status().isCreated());
        createAndGetId(OWNER, saveUser("민서").getId());
        teamService.disband(leaderId, disbandedId, new TeamStatusUpdateRequest(TeamStatus.DISBANDED));

        mockMvc.perform(get(BASE + "?targetType=TEAM").header("Authorization", bearer(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].targetId").value(disbandedId))
                .andExpect(jsonPath("$.data.content[0].target").value(nullValue()))
                .andExpect(jsonPath("$.data.content[1].target.name").value("살아있는밴드"));
        mockMvc.perform(get(BASE).header("Authorization", bearer(OWNER)))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[2].target.name").value("살아있는밴드"));
    }

    @Test
    @DisplayName("임시: SONG 저장은 400이고, targetType·targetId 누락·잘못된 값도 400이다")
    void create_unsupportedOrInvalid_returns400() throws Exception {
        create(OWNER, "SONG", 5L).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        create(OWNER, "GROUP", 5L).andExpect(status().isBadRequest());
        mockMvc.perform(post(BASE).header("Authorization", bearer(OWNER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetType\":\"USER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("목록: 본인 저장만 최신순으로, 페이지 응답 모양을 따른다")
    void list_ownOnly_latestFirst() throws Exception {
        User a = saveUser("가나");
        User b = saveUser("다라");
        createAndGetId(OWNER, a.getId());
        createAndGetId(OWNER, b.getId());
        createAndGetId(OTHER_OWNER, a.getId());

        mockMvc.perform(get(BASE).header("Authorization", bearer(OWNER)))
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
        createAndGetId(OWNER, a.getId());

        mockMvc.perform(get(BASE + "?targetType=USER").header("Authorization", bearer(OWNER)))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get(BASE + "?targetType=TEAM").header("Authorization", bearer(OWNER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get(BASE + "?targetType=GROUP").header("Authorization", bearer(OWNER)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + "?sort=bogus,asc").header("Authorization", bearer(OWNER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("목록: 대상이 탈퇴하면 항목은 남고 target이 null이다 (삭제된 항목으로 표시)")
    void list_withdrawnTarget_hasNullTarget() throws Exception {
        User target = saveUser("나갈사람");
        createAndGetId(OWNER, target.getId());
        userWithdrawService.withdraw(target.getId());

        mockMvc.perform(get(BASE).header("Authorization", bearer(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].targetId").value(target.getId()))
                .andExpect(jsonPath("$.data.content[0]", hasKey("target")))
                .andExpect(jsonPath("$.data.content[0].target").value(nullValue()));
    }

    @Test
    @DisplayName("삭제: 본인 저장은 200과 {id, deleted:true}를 돌려주고 목록에서 사라진다")
    void delete_own() throws Exception {
        User target = saveUser("민서");
        long id = createAndGetId(OWNER, target.getId());

        mockMvc.perform(delete(BASE + "/" + id).header("Authorization", bearer(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.deleted").value(true));
        mockMvc.perform(get(BASE).header("Authorization", bearer(OWNER)))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("삭제: 남의 저장·없는 저장은 똑같이 404 BOOKMARK_NOT_FOUND다")
    void delete_othersOrMissing_returns404() throws Exception {
        User target = saveUser("민서");
        long id = createAndGetId(OWNER, target.getId());

        mockMvc.perform(delete(BASE + "/" + id).header("Authorization", bearer(OTHER_OWNER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKMARK_NOT_FOUND"));
        mockMvc.perform(delete(BASE + "/999999").header("Authorization", bearer(OWNER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKMARK_NOT_FOUND"));
    }
}
