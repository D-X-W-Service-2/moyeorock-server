package com.moyeorock.domain.setlist.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.song.entity.Song;
import com.moyeorock.domain.song.repository.SongRepository;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.repository.UserRepository;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.security.JwtProvider;
import java.time.LocalDateTime;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * 팀 선택곡 저장·조회를 실제 HTTP 흐름으로 확인한다. 팀은 팀 생성 API로 만들고, 곡을 넣는 API·가입 승인 API가 없어서
 * 곡은 Repository로, 팀원은 2팀 TeamService.addMember로 직접 넣는다. 클래스 단위 {@code @Transactional}이라 테스트마다 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class SetlistApiTest {

    private static final long NON_MEMBER_ID = 999_999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SongRepository songRepository;

    @Autowired
    private TeamService teamService;

    private long leaderId;
    private long memberId;
    private long teamId;
    private long bohemianId;
    private long creepId;

    @BeforeEach
    void setUp() throws Exception {
        leaderId = saveUser("팀장");
        memberId = saveUser("팀원");
        teamId = createTeam(leaderId);
        teamService.addMember(teamId, memberId, Instrument.DRUM);
        bohemianId = songRepository.save(Song.create("Bohemian Rhapsody", "Queen", Genre.ROCK, null, null, null, null)).getId();
        creepId = songRepository.save(Song.create("Creep", "Radiohead", Genre.ROCK, null, null, null, null)).getId();
    }

    @Test
    @DisplayName("팀장이 선택곡을 저장하면 요청 순서대로 돌려주고, 팀원이 조회하면 같은 목록이다")
    void save_thenGet_returnsSongsInRequestOrder() throws Exception {
        saveSongs(leaderId, "[" + creepId + "," + bohemianId + "]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamId").value(teamId))
                .andExpect(jsonPath("$.data.songs.length()").value(2))
                .andExpect(jsonPath("$.data.songs[0].song.title").value("Creep"))
                .andExpect(jsonPath("$.data.songs[0].song.artist").value("Radiohead"))
                .andExpect(jsonPath("$.data.songs[1].song.id").value(bohemianId))
                .andExpect(jsonPath("$.data.songs[0].id").exists());

        getSongs(memberId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.songs.length()").value(2))
                .andExpect(jsonPath("$.data.songs[0].song.title").value("Creep"));
    }

    @Test
    @DisplayName("다시 저장하면 전체가 바뀐다 — 겹치는 곡을 다시 넣어도 되고, 빈 목록이면 모두 지운다")
    void save_replacesWholeList() throws Exception {
        saveSongs(leaderId, "[" + bohemianId + "]").andExpect(status().isOk());

        saveSongs(leaderId, "[" + bohemianId + "," + creepId + "]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.songs.length()").value(2));
        saveSongs(leaderId, "[]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.songs").isEmpty());
    }

    @Test
    @DisplayName("팀장이 아닌 팀원이 저장하면 403 NOT_TEAM_LEADER다")
    void save_byMember_returns403() throws Exception {
        saveSongs(memberId, "[" + bohemianId + "]")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_TEAM_LEADER"));
    }

    @Test
    @DisplayName("없는 곡이 섞이면 404 SONG_NOT_FOUND, 같은 곡이 두 번이거나 목록이 없으면 400 VALIDATION_FAILED다")
    void save_withInvalidSongs_returnsError() throws Exception {
        saveSongs(leaderId, "[" + bohemianId + ",999999]")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SONG_NOT_FOUND"));
        saveSongs(leaderId, "[" + bohemianId + "," + bohemianId + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(put("/v1/teams/" + teamId + "/songs").header("Authorization", bearer(leaderId))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("팀원이 아니면 선택곡을 볼 수 없다 — 403 NO_PERMISSION")
    void get_byNonMember_returns403() throws Exception {
        getSongs(NON_MEMBER_ID)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_PERMISSION"));
    }

    @Test
    @DisplayName("해체된 팀의 선택곡은 저장·조회 모두 404 TEAM_NOT_FOUND다")
    void disbandedTeam_returns404() throws Exception {
        mockMvc.perform(patch("/v1/teams/" + teamId + "/status").header("Authorization", bearer(leaderId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISBANDED\"}"))
                .andExpect(status().isOk());

        saveSongs(leaderId, "[" + bohemianId + "]")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
        getSongs(memberId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
    }

    private ResultActions saveSongs(long userId, String songIdsJson) throws Exception {
        return mockMvc.perform(put("/v1/teams/" + teamId + "/songs").header("Authorization", bearer(userId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"songIds\":" + songIdsJson + "}"));
    }

    private ResultActions getSongs(long userId) throws Exception {
        return mockMvc.perform(get("/v1/teams/" + teamId + "/songs").header("Authorization", bearer(userId)));
    }

    private String bearer(long userId) {
        return "Bearer " + jwtProvider.generateToken(userId);
    }

    private long saveUser(String nickname) {
        return userRepository.save(User.signupWithEmail(
                nickname + "@moyeorock.test", "hash", nickname, LocalDateTime.now())).getId();
    }

    private long createTeam(long userId) throws Exception {
        String json = mockMvc.perform(post("/v1/teams").header("Authorization", bearer(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"무명밴드","region":"SEOUL","genres":["ROCK"],"instrument":"BASS"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.data.id")).longValue();
    }
}
