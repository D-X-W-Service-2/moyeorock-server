package com.moyeorock.domain.performance.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.group.entity.GroupMember;
import com.moyeorock.domain.group.enums.GroupRole;
import com.moyeorock.domain.group.repository.GroupMemberRepository;
import com.moyeorock.domain.performance.enums.PerformanceStatus;
import com.moyeorock.domain.performance.repository.PerformanceRepository;
import com.moyeorock.domain.song.entity.Song;
import com.moyeorock.domain.song.repository.SongRepository;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.repository.UserRepository;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.security.JwtProvider;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공연 셋리스트 조회·넘기기·확정·확정 해제를 실제 HTTP 흐름으로 확인한다. 공연 내 팀 생성 API(#104)와 가입·곡 등록 API가 아직 없어서
 * 공연 팀은 2팀 TeamService.createForPerformance로, 모임원은 Repository로, 곡은 Repository로 넣는다.
 * 클래스 단위 {@code @Transactional}이라 테스트마다 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class PerformanceSetlistApiTest {

    private static final long NON_MEMBER_ID = 999_999L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GroupMemberRepository groupMemberRepository;
    @Autowired
    private PerformanceRepository performanceRepository;
    @Autowired
    private SongRepository songRepository;
    @Autowired
    private TeamService teamService;

    private long ownerId;
    private long leaderAId;
    private long leaderBId;
    private long performanceId;
    private long teamAId;
    private long teamBId;
    private long bohemianId;
    private long creepId;
    private long wonderwallId;

    @BeforeEach
    void setUp() throws Exception {
        ownerId = saveUser("모임장");
        leaderAId = saveUser("A팀장");
        leaderBId = saveUser("B팀장");
        long groupId = createGroup(ownerId);
        groupMemberRepository.save(GroupMember.create(groupId, leaderAId, GroupRole.MEMBER));
        groupMemberRepository.save(GroupMember.create(groupId, leaderBId, GroupRole.MEMBER));
        performanceId = createPerformance(ownerId, groupId);
        teamAId = createPerformanceTeam(leaderAId, "A팀");
        teamBId = createPerformanceTeam(leaderBId, "B팀");
        bohemianId = saveSong("Bohemian Rhapsody", "Queen");
        creepId = saveSong("Creep", "Radiohead");
        wonderwallId = saveSong("Wonderwall", "Oasis");
        saveTeamSongs(leaderAId, teamAId, List.of(bohemianId, creepId));
        saveTeamSongs(leaderBId, teamBId, List.of(bohemianId, wonderwallId));
    }

    @Test
    @DisplayName("팀장이 선택곡 중 곡을 넘기면 넘긴 뒤의 셋리스트를 돌려주고, 모임원 조회도 팀별·넘긴 순으로 같다")
    void submit_thenGet_returnsSetlist() throws Exception {
        submit(leaderAId, teamAId, "[" + creepId + "," + bohemianId + "]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.performanceId").value(performanceId))
                .andExpect(jsonPath("$.data.confirmed").value(false))
                .andExpect(jsonPath("$.data.setlist.length()").value(2))
                .andExpect(jsonPath("$.data.setlist[0].teamId").value(teamAId))
                .andExpect(jsonPath("$.data.setlist[0].teamName").value("A팀"))
                .andExpect(jsonPath("$.data.setlist[0].song.title").value("Creep"))
                .andExpect(jsonPath("$.data.setlist[0].sortOrder").doesNotExist());
        submit(leaderBId, teamBId, "[" + wonderwallId + "]").andExpect(status().isOk());

        getSetlist(ownerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.setlist.length()").value(3))
                .andExpect(jsonPath("$.data.setlist[2].teamName").value("B팀"))
                .andExpect(jsonPath("$.data.setlist[2].song.title").value("Wonderwall"));
    }

    @Test
    @DisplayName("다시 넘기면 그 팀이 넘긴 곡 전체가 바뀐다 — 빈 목록이면 모두 빠진다")
    void resubmit_replacesTeamSongs() throws Exception {
        submit(leaderAId, teamAId, "[" + bohemianId + "]").andExpect(status().isOk());

        submit(leaderAId, teamAId, "[" + creepId + "]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.setlist.length()").value(1))
                .andExpect(jsonPath("$.data.setlist[0].song.title").value("Creep"));
        submit(leaderAId, teamAId, "[]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.setlist").isEmpty());
    }

    @Test
    @DisplayName("같은 공연의 다른 팀이 이미 넘긴 곡은 409 SONG_ALREADY_IN_SETLIST, 팀 선택곡에 없는 곡은 409 SONG_NOT_SELECTED_BY_TEAM이다")
    void submit_conflicts() throws Exception {
        submit(leaderAId, teamAId, "[" + bohemianId + "]").andExpect(status().isOk());

        submit(leaderBId, teamBId, "[" + bohemianId + "]")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SONG_ALREADY_IN_SETLIST"));
        submit(leaderAId, teamAId, "[" + wonderwallId + "]")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SONG_NOT_SELECTED_BY_TEAM"));
    }

    @Test
    @DisplayName("셋리스트가 확정됐으면 넘기기는 409 INVALID_STATE다")
    void submit_whenConfirmed_returns409() throws Exception {
        ReflectionTestUtils.setField(performanceRepository.findById(performanceId).orElseThrow(),
                "setlistConfirmedAt", LocalDateTime.now());

        submit(leaderAId, teamAId, "[" + bohemianId + "]")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("다른 팀의 팀장이 넘기면 403 NOT_TEAM_LEADER, 이 공연 소속이 아닌 팀이면 404 TEAM_NOT_FOUND다")
    void submit_byWrongTeam_returnsError() throws Exception {
        submit(leaderBId, teamAId, "[" + bohemianId + "]")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_TEAM_LEADER"));

        long independentTeamId = createIndependentTeam(leaderAId);
        submit(leaderAId, independentTeamId, "[" + bohemianId + "]")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
    }

    @Test
    @DisplayName("곡 목록이 없으면 400, 없는 공연이면 404 PERFORMANCE_NOT_FOUND다")
    void submit_invalidRequest() throws Exception {
        mockMvc.perform(put("/v1/performances/" + performanceId + "/setlist/teams/" + teamAId)
                        .header("Authorization", bearer(leaderAId))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(put("/v1/performances/999999/setlist/teams/" + teamAId)
                        .header("Authorization", bearer(leaderAId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"songIds\":[]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERFORMANCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("모임원이 아니면 공연 셋리스트를 볼 수 없다 — 403 NOT_GROUP_MEMBER")
    void get_byNonMember_returns403() throws Exception {
        getSetlist(NON_MEMBER_ID)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_GROUP_MEMBER"));
    }

    @Test
    @DisplayName("모임장이 고른 곡만 순서대로 확정되고 나머지는 빠진다 → 넘기기 409 → 해제하면 순서가 비고 다시 넘길 수 있다")
    void confirm_thenUnconfirm_flow() throws Exception {
        submit(leaderAId, teamAId, "[" + creepId + "," + bohemianId + "]").andExpect(status().isOk());
        submit(leaderBId, teamBId, "[" + wonderwallId + "]").andExpect(status().isOk());

        confirm(ownerId, item(teamBId, wonderwallId, 1) + "," + item(teamAId, creepId, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmed").value(true))
                .andExpect(jsonPath("$.data.setlist.length()").value(2))
                .andExpect(jsonPath("$.data.setlist[0].song.title").value("Wonderwall"))
                .andExpect(jsonPath("$.data.setlist[0].sortOrder").value(1))
                .andExpect(jsonPath("$.data.setlist[1].song.title").value("Creep"))
                .andExpect(jsonPath("$.data.setlist[1].sortOrder").value(2));
        getSetlist(leaderAId)
                .andExpect(jsonPath("$.data.confirmed").value(true))
                .andExpect(jsonPath("$.data.setlist.length()").value(2));
        submit(leaderAId, teamAId, "[" + bohemianId + "]")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        unconfirm(ownerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmed").value(false))
                .andExpect(jsonPath("$.data.setlist.length()").value(2))
                .andExpect(jsonPath("$.data.setlist[0].sortOrder").doesNotExist());
        submit(leaderAId, teamAId, "[" + creepId + "," + bohemianId + "]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.setlist.length()").value(3));
    }

    @Test
    @DisplayName("확정된 셋리스트도 다시 확정할 수 있다 — 남아 있는 곡 안에서 고르고 순서를 바꾼다")
    void reconfirm_withinConfirmedSongs() throws Exception {
        submit(leaderAId, teamAId, "[" + creepId + "," + bohemianId + "]").andExpect(status().isOk());
        confirm(ownerId, item(teamAId, creepId, 1) + "," + item(teamAId, bohemianId, 2)).andExpect(status().isOk());

        confirm(ownerId, item(teamAId, bohemianId, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.setlist.length()").value(1))
                .andExpect(jsonPath("$.data.setlist[0].song.title").value("Bohemian Rhapsody"));
    }

    @Test
    @DisplayName("팀이 넘기지 않은 곡이나 다른 팀 곡으로 적은 항목은 409 SONG_NOT_TEAM_CANDIDATE, 이 공연 팀이 아니면 404 TEAM_NOT_FOUND다")
    void confirm_rejectsNonCandidates() throws Exception {
        submit(leaderAId, teamAId, "[" + creepId + "]").andExpect(status().isOk());
        submit(leaderBId, teamBId, "[" + wonderwallId + "]").andExpect(status().isOk());

        confirm(ownerId, item(teamAId, bohemianId, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SONG_NOT_TEAM_CANDIDATE"));
        confirm(ownerId, item(teamAId, wonderwallId, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SONG_NOT_TEAM_CANDIDATE"));
        long independentTeamId = createIndependentTeam(leaderAId);
        confirm(ownerId, item(independentTeamId, creepId, 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
        getSetlist(ownerId)
                .andExpect(jsonPath("$.data.confirmed").value(false))
                .andExpect(jsonPath("$.data.setlist.length()").value(2));
    }

    @Test
    @DisplayName("빈 목록·순서가 1부터 빈틈없지 않은 요청은 400, 모임장이 아니면 403 NOT_GROUP_OWNER다")
    void confirm_invalidRequestOrNotOwner() throws Exception {
        submit(leaderAId, teamAId, "[" + creepId + "," + bohemianId + "]").andExpect(status().isOk());

        confirm(ownerId, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        confirm(ownerId, item(teamAId, creepId, 1) + "," + item(teamAId, bohemianId, 3))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        confirm(leaderAId, item(teamAId, creepId, 1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_GROUP_OWNER"));
        unconfirm(leaderAId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_GROUP_OWNER"));
    }

    @Test
    @DisplayName("확정되지 않은 셋리스트의 해제, 끝난 공연의 확정·해제는 409 INVALID_STATE다")
    void confirmAndUnconfirm_invalidState() throws Exception {
        submit(leaderAId, teamAId, "[" + creepId + "]").andExpect(status().isOk());
        unconfirm(ownerId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        confirm(ownerId, item(teamAId, creepId, 1)).andExpect(status().isOk());

        performanceRepository.findById(performanceId).orElseThrow().changeStatus(PerformanceStatus.DONE);

        confirm(ownerId, item(teamAId, creepId, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        unconfirm(ownerId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("공연 상세의 setlist는 확정 전엔 빈 목록이고, 확정하면 확정곡만 순서대로 담기며, 공연이 끝나 팀이 해체돼도 그대로 보인다")
    void detail_showsOnlyConfirmedSetlist_andKeepsItAfterPerformanceEnds() throws Exception {
        submit(leaderAId, teamAId, "[" + creepId + "," + bohemianId + "]").andExpect(status().isOk());
        submit(leaderBId, teamBId, "[" + wonderwallId + "]").andExpect(status().isOk());

        getDetail(leaderAId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teams.length()").value(2))
                .andExpect(jsonPath("$.data.setlist").isArray())
                .andExpect(jsonPath("$.data.setlist").isEmpty());

        confirm(ownerId, item(teamBId, wonderwallId, 1) + "," + item(teamAId, creepId, 2)).andExpect(status().isOk());

        getDetail(leaderAId)
                .andExpect(jsonPath("$.data.setlist.length()").value(2))
                .andExpect(jsonPath("$.data.setlist[0].teamId").value(teamBId))
                .andExpect(jsonPath("$.data.setlist[0].teamName").value("B팀"))
                .andExpect(jsonPath("$.data.setlist[0].song.title").value("Wonderwall"))
                .andExpect(jsonPath("$.data.setlist[0].song.artist").value("Oasis"))
                .andExpect(jsonPath("$.data.setlist[0].sortOrder").value(1))
                .andExpect(jsonPath("$.data.setlist[1].song.title").value("Creep"))
                .andExpect(jsonPath("$.data.setlist[1].sortOrder").value(2));

        mockMvc.perform(patch("/v1/performances/" + performanceId + "/status").header("Authorization", bearer(ownerId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk());

        getDetail(ownerId)
                .andExpect(jsonPath("$.data.teams").isEmpty())
                .andExpect(jsonPath("$.data.setlist.length()").value(2))
                .andExpect(jsonPath("$.data.setlist[0].teamName").value("B팀"))
                .andExpect(jsonPath("$.data.setlist[1].teamName").value("A팀"));
    }

    @Test
    @DisplayName("확정을 해제하면 공연 상세의 setlist는 다시 빈 목록이다")
    void detail_returnsEmptySetlist_afterUnconfirm() throws Exception {
        submit(leaderAId, teamAId, "[" + creepId + "]").andExpect(status().isOk());
        confirm(ownerId, item(teamAId, creepId, 1)).andExpect(status().isOk());
        getDetail(leaderAId).andExpect(jsonPath("$.data.setlist.length()").value(1));

        unconfirm(ownerId).andExpect(status().isOk());

        getDetail(leaderAId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.setlist").isArray())
                .andExpect(jsonPath("$.data.setlist").isEmpty());
    }

    @Test
    @DisplayName("확정 뒤 팀이 개별 해체되면 그 팀 곡은 상세에서 빠져 순서 번호가 비고, 모든 팀이 해체되면 확정 상태여도 빈 목록이다")
    void detail_dropsDisbandedTeamSongs_evenWhenConfirmed() throws Exception {
        submit(leaderAId, teamAId, "[" + creepId + "]").andExpect(status().isOk());
        submit(leaderBId, teamBId, "[" + wonderwallId + "]").andExpect(status().isOk());
        confirm(ownerId, item(teamBId, wonderwallId, 1) + "," + item(teamAId, creepId, 2)).andExpect(status().isOk());

        disbandTeam(leaderBId, teamBId);

        getDetail(leaderAId)
                .andExpect(jsonPath("$.data.setlist.length()").value(1))
                .andExpect(jsonPath("$.data.setlist[0].song.title").value("Creep"))
                .andExpect(jsonPath("$.data.setlist[0].sortOrder").value(2));

        disbandTeam(leaderAId, teamAId);

        // 빈 목록이 곧 확정 전은 아니다 — 확정 여부는 셋리스트 조회의 confirmed로 본다
        getDetail(ownerId).andExpect(jsonPath("$.data.setlist").isEmpty());
        getSetlist(ownerId)
                .andExpect(jsonPath("$.data.confirmed").value(true))
                .andExpect(jsonPath("$.data.setlist").isEmpty());
    }

    private void disbandTeam(long leaderId, long teamId) throws Exception {
        mockMvc.perform(patch("/v1/teams/" + teamId + "/status").header("Authorization", bearer(leaderId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISBANDED\"}"))
                .andExpect(status().isOk());
    }

    private ResultActions getDetail(long userId) throws Exception {
        return mockMvc.perform(get("/v1/performances/" + performanceId).header("Authorization", bearer(userId)));
    }

    private ResultActions submit(long userId, long teamId, String songIdsJson) throws Exception {
        return mockMvc.perform(put("/v1/performances/" + performanceId + "/setlist/teams/" + teamId)
                .header("Authorization", bearer(userId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"songIds\":" + songIdsJson + "}"));
    }

    private ResultActions confirm(long userId, String itemsJson) throws Exception {
        return mockMvc.perform(put("/v1/performances/" + performanceId + "/setlist")
                .header("Authorization", bearer(userId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[" + itemsJson + "]}"));
    }

    private ResultActions unconfirm(long userId) throws Exception {
        return mockMvc.perform(patch("/v1/performances/" + performanceId + "/setlist/unconfirm")
                .header("Authorization", bearer(userId)));
    }

    private static String item(long teamId, long songId, int sortOrder) {
        return "{\"teamId\":%d,\"songId\":%d,\"sortOrder\":%d}".formatted(teamId, songId, sortOrder);
    }

    private ResultActions getSetlist(long userId) throws Exception {
        return mockMvc.perform(get("/v1/performances/" + performanceId + "/setlist")
                .header("Authorization", bearer(userId)));
    }

    private void saveTeamSongs(long userId, long teamId, List<Long> songIds) throws Exception {
        mockMvc.perform(put("/v1/teams/" + teamId + "/songs").header("Authorization", bearer(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"songIds\":" + songIds + "}"))
                .andExpect(status().isOk());
    }

    private String bearer(long userId) {
        return "Bearer " + jwtProvider.generateToken(userId);
    }

    private long saveUser(String nickname) {
        return userRepository.save(User.signupWithEmail(
                nickname + "@moyeorock.test", "hash", nickname, LocalDateTime.now())).getId();
    }

    private long saveSong(String title, String artist) {
        return songRepository.save(Song.create(title, artist, Genre.ROCK, null, null, null, null)).getId();
    }

    private long createGroup(long userId) throws Exception {
        return readId(mockMvc.perform(post("/v1/groups").header("Authorization", bearer(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"홍대 밴드부","type":"REGULAR","region":"SEOUL"}
                        """)));
    }

    private long createPerformance(long userId, long groupId) throws Exception {
        String performedAt = LocalDate.now().plusMonths(1).atTime(18, 0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return readId(mockMvc.perform(post("/v1/groups/" + groupId + "/performances").header("Authorization", bearer(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"2026 정기공연","performedAt":"%s","venue":"홍대 롤링홀"}
                        """.formatted(performedAt))));
    }

    private long createPerformanceTeam(long leaderId, String name) {
        return teamService.createForPerformance(leaderId, performanceId, name, null, Region.SEOUL, List.of(Genre.ROCK),
                Instrument.BASS).id();
    }

    private long createIndependentTeam(long leaderId) throws Exception {
        return readId(mockMvc.perform(post("/v1/teams").header("Authorization", bearer(leaderId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"독립팀","region":"SEOUL","genres":["ROCK"],"instrument":"BASS"}
                        """)));
    }

    private long readId(ResultActions result) throws Exception {
        String json = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.data.id")).longValue();
    }
}
