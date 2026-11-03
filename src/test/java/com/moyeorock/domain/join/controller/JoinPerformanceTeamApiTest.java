package com.moyeorock.domain.join.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.group.dto.request.GroupCreateRequest;
import com.moyeorock.domain.group.enums.GroupType;
import com.moyeorock.domain.group.service.GroupMemberService;
import com.moyeorock.domain.group.service.GroupService;
import com.moyeorock.domain.join.repository.JoinRequestRepository;
import com.moyeorock.domain.performance.entity.Performance;
import com.moyeorock.domain.performance.repository.PerformanceRepository;
import com.moyeorock.domain.team.service.TeamMemberService;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.repository.UserRepository;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.security.JwtProvider;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공연 팀에는 그 공연을 연 모임의 활동 모임원만 들어올 수 있다(#96). 가입 신청·초대를 만들 때 미리 막고,
 * 승인·수락 때는 TeamService.addMember가 다시 막는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class JoinPerformanceTeamApiTest {

    private long leaderId;       // 모임장이자 공연 팀 팀장
    private long groupMemberId;  // 공연을 연 모임의 모임원
    private long outsiderId;     // 모임과 무관한 사람
    private long groupId;
    private long teamId;         // 공연 팀

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GroupService groupService;
    @Autowired
    private GroupMemberService groupMemberService;
    @Autowired
    private PerformanceRepository performanceRepository;
    @Autowired
    private TeamService teamService;
    @Autowired
    private TeamMemberService teamMemberService;
    @Autowired
    private JoinRequestRepository joinRequestRepository;

    @BeforeEach
    void setUp() {
        leaderId = saveUser("리더").getId();
        groupMemberId = saveUser("모임원").getId();
        outsiderId = saveUser("외부인").getId();
        groupId = groupService.create(leaderId,
                new GroupCreateRequest("홍대동아리", null, GroupType.REGULAR, Region.SEOUL, null)).id();
        groupMemberService.addMember(groupId, groupMemberId);
        long performanceId = performanceRepository.save(Performance.create(groupId, leaderId, "정기공연", null,
                LocalDateTime.now().plusDays(30), "홍대 클럽", null)).getId();
        teamId = teamService.createForPerformance(leaderId, performanceId, "공연팀", null, Region.SEOUL,
                List.of(Genre.ROCK), Instrument.EL_GT).id();
    }

    private User saveUser(String nickname) {
        return userRepository.saveAndFlush(User.signupWithEmail(
                nickname + "@example.com", "hash", nickname, LocalDateTime.now()));
    }

    private ResultActions send(MockHttpServletRequestBuilder builder, long userId, String body) throws Exception {
        builder.header("Authorization", "Bearer " + jwtProvider.generateToken(userId))
                .contentType(MediaType.APPLICATION_JSON).content(body);
        return mockMvc.perform(builder);
    }

    private ResultActions apply(long userId) throws Exception {
        return send(post("/v1/join-requests"), userId,
                "{\"targetType\":\"TEAM\",\"targetId\":" + teamId + ",\"instrument\":\"BASS\"}");
    }

    private ResultActions invite(long inviteeId) throws Exception {
        return send(post("/v1/invitations"), leaderId,
                "{\"targetType\":\"TEAM\",\"targetId\":" + teamId + ",\"inviteeId\":" + inviteeId
                        + ",\"instrument\":\"DRUM\"}");
    }

    private long idOf(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.id"))
                .longValue();
    }

    @Test
    @DisplayName("모임 밖 사람이 공연 팀에 신청하면 403 NOT_PERFORMANCE_GROUP_MEMBER이고 신청이 저장되지 않는다")
    void apply_byOutsider_returns403() throws Exception {
        apply(outsiderId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_PERFORMANCE_GROUP_MEMBER"));

        assertThat(joinRequestRepository.count()).isZero();
    }

    @Test
    @DisplayName("그 공연을 연 모임의 모임원은 공연 팀에 신청할 수 있다")
    void apply_byGroupMember_returns201() throws Exception {
        apply(groupMemberId).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("모임 밖 사람을 공연 팀에 초대하면 403이고, 모임원은 초대할 수 있다")
    void invite_outsiderRejected_groupMemberAccepted() throws Exception {
        invite(outsiderId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_PERFORMANCE_GROUP_MEMBER"));
        assertThat(joinRequestRepository.count()).isZero();

        invite(groupMemberId).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("신청한 뒤 모임을 나갔다면 승인할 때 403이고 신청은 대기로 남는다 — TeamService.addMember가 다시 확인한다")
    void approve_afterLeavingGroup_returns403() throws Exception {
        long requestId = idOf(apply(groupMemberId).andExpect(status().isCreated()));
        groupMemberService.changeStatus(groupMemberId, groupId, groupMemberId);

        send(patch("/v1/join-requests/" + requestId + "/status"), leaderId, "{\"status\":\"APPROVED\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_PERFORMANCE_GROUP_MEMBER"));

        assertThat(teamMemberService.isActiveMember(teamId, groupMemberId)).isFalse();
        assertThat(joinRequestRepository.findById(requestId).orElseThrow().isPending()).isTrue();
    }

    @Test
    @DisplayName("초대받은 뒤 모임을 나갔다면 수락할 때 403이다")
    void accept_afterLeavingGroup_returns403() throws Exception {
        long invitationId = idOf(invite(groupMemberId).andExpect(status().isCreated()));
        groupMemberService.changeStatus(groupMemberId, groupId, groupMemberId);

        send(patch("/v1/invitations/" + invitationId + "/status"), groupMemberId, "{\"status\":\"ACCEPTED\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_PERFORMANCE_GROUP_MEMBER"));

        assertThat(teamMemberService.isActiveMember(teamId, groupMemberId)).isFalse();
    }
}
