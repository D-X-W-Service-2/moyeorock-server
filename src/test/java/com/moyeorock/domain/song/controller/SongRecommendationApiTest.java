package com.moyeorock.domain.song.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.song.client.AiSongRecommendRequest;
import com.moyeorock.domain.song.client.AiSongRecommendResponse;
import com.moyeorock.domain.song.client.AiSongRecommendationClient;
import com.moyeorock.domain.song.entity.Song;
import com.moyeorock.domain.song.repository.SongRepository;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.entity.UserInstrument;
import com.moyeorock.domain.user.repository.UserInstrumentRepository;
import com.moyeorock.domain.user.repository.UserRepository;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.security.JwtProvider;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팀 곡 추천(이슈 #171)·내 취향 추천곡(이슈 #172)을 실제 HTTP 흐름으로 확인한다. AI 서버만 가짜({@code @MockitoBean})로 바꾸고 팀·팀원·실력·곡은 실제 DB에 넣는다.
 * 팀은 팀 생성 API로, 팀원은 2팀 TeamService.addMember로, 곡·실력은 Repository로 넣는다. 클래스 단위 {@code @Transactional}이라
 * 테스트마다 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class SongRecommendationApiTest {

    private static final long NON_MEMBER_ID = 999_999L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserInstrumentRepository userInstrumentRepository;
    @Autowired
    private SongRepository songRepository;
    @Autowired
    private TeamService teamService;

    @MockitoBean
    private AiSongRecommendationClient aiSongRecommendationClient;

    private long leaderId;
    private long memberId;
    private long teamId;
    private long creepId;

    @BeforeEach
    void setUp() throws Exception {
        leaderId = saveUser("팀장");
        memberId = saveUser("팀원");
        userInstrumentRepository.save(UserInstrument.create(leaderId, Instrument.BASS, Level.INTERMEDIATE));
        userInstrumentRepository.save(UserInstrument.create(memberId, Instrument.DRUM, Level.BEGINNER));
        teamId = createTeam(leaderId);
        teamService.addMember(teamId, memberId, Instrument.DRUM);
        creepId = songRepository.save(Song.create("Creep", "Radiohead", Genre.ROCK, "G", 92,
                Map.of(Instrument.BASS, Level.NOVICE, Instrument.DRUM, Level.INTERMEDIATE), "ext-creep")).getId();
        songRepository.save(Song.create("Plastic Love", "Mariya Takeuchi", Genre.POP, null, null, null, "ext-plastic"));
    }

    @Test
    @DisplayName("팀원이 부르면 AI가 고른 곡을 우리 곡 전체 정보·점수·근거와 함께 AI 순서대로 돌려준다")
    void recommend_member_returnsSongs() throws Exception {
        given(aiSongRecommendationClient.isConfigured()).willReturn(true);
        given(aiSongRecommendationClient.recommend(any())).willReturn(new AiSongRecommendResponse(List.of(
                new AiSongRecommendResponse.Item("ext-creep", 0.875),
                new AiSongRecommendResponse.Item("ext-not-synced", 0.8),
                new AiSongRecommendResponse.Item("ext-plastic", 0.5)), "content_rule"));

        recommend(memberId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamId").value(teamId))
                .andExpect(jsonPath("$.data.songs.length()").value(2))
                .andExpect(jsonPath("$.data.songs[0].song.id").value(creepId))
                .andExpect(jsonPath("$.data.songs[0].song.title").value("Creep"))
                .andExpect(jsonPath("$.data.songs[0].song.genre").value("ROCK"))
                .andExpect(jsonPath("$.data.songs[0].song.bpm").value(92))
                .andExpect(jsonPath("$.data.songs[0].song.difficulty[0].part").value("BASS"))
                .andExpect(jsonPath("$.data.songs[0].score").value(0.875))
                .andExpect(jsonPath("$.data.songs[0].reasons[0]").value("팀 장르 일치: 록"))
                .andExpect(jsonPath("$.data.songs[0].reasons[1]").value("베이스 실력 충족"))
                .andExpect(jsonPath("$.data.songs[0].reasons[2]").value("드럼 난이도 초과"))
                .andExpect(jsonPath("$.data.songs[0].selectedByOtherTeam").value(false))
                .andExpect(jsonPath("$.data.songs[1].song.title").value("Plastic Love"))
                .andExpect(jsonPath("$.data.songs[1].song.difficulty").isEmpty())
                .andExpect(jsonPath("$.data.songs[1].reasons").isEmpty());

        ArgumentCaptor<AiSongRecommendRequest> captor = ArgumentCaptor.forClass(AiSongRecommendRequest.class);
        verify(aiSongRecommendationClient).recommend(captor.capture());
        assertThat(captor.getValue().mode()).isEqualTo("TEAM");
        assertThat(captor.getValue().genres()).containsExactly(Genre.ROCK);
        assertThat(captor.getValue().members()).containsExactlyInAnyOrder(
                new AiSongRecommendRequest.Member(Instrument.BASS, Level.INTERMEDIATE),
                new AiSongRecommendRequest.Member(Instrument.DRUM, Level.BEGINNER));
    }

    @Test
    @DisplayName("팀원이 아니면 403 NO_PERMISSION, 없는 팀이면 404 TEAM_NOT_FOUND다")
    void recommend_permissionAndMissingTeam() throws Exception {
        recommend(NON_MEMBER_ID)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_PERMISSION"));

        mockMvc.perform(get("/v1/teams/999999/songs/recommendations").header("Authorization", bearer(leaderId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
    }

    @Test
    @DisplayName("해체된 팀이면 404 TEAM_NOT_FOUND다")
    void recommend_disbandedTeam_returns404() throws Exception {
        mockMvc.perform(patch("/v1/teams/" + teamId + "/status").header("Authorization", bearer(leaderId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISBANDED\"}"))
                .andExpect(status().isOk());

        recommend(leaderId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
    }

    @Test
    @DisplayName("AI 서버 주소가 없으면 503 SONG_RECOMMENDATION_UNAVAILABLE이다")
    void recommend_aiNotConfigured_returns503() throws Exception {
        given(aiSongRecommendationClient.isConfigured()).willReturn(false);

        recommend(leaderId)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SONG_RECOMMENDATION_UNAVAILABLE"));
    }

    @Test
    @DisplayName("인증이 없으면 401이다")
    void recommend_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/v1/teams/" + teamId + "/songs/recommendations"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 내 취향 추천곡 (이슈 #172) ----------

    @Test
    @DisplayName("내 취향 추천곡: 온보딩을 마친 사용자는 내 장르·실력으로 AI를 부르고, 우리 곡으로 바꿔 최대 2곡을 돌려준다")
    void recommendForMe_onboarded_returnsTopTwo() throws Exception {
        User leader = userRepository.findById(leaderId).orElseThrow();
        leader.completeOnboarding("팀장", Region.SEOUL, List.of(Genre.POP));
        userRepository.saveAndFlush(leader);
        given(aiSongRecommendationClient.isConfigured()).willReturn(true);
        given(aiSongRecommendationClient.recommend(any())).willReturn(new AiSongRecommendResponse(List.of(
                new AiSongRecommendResponse.Item("ext-not-synced", 0.95),
                new AiSongRecommendResponse.Item("ext-plastic", 0.9),
                new AiSongRecommendResponse.Item("ext-creep", 0.6)), "content_rule"));

        recommendForMe(leaderId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.songs.length()").value(2))
                .andExpect(jsonPath("$.data.songs[0].song.title").value("Plastic Love"))
                .andExpect(jsonPath("$.data.songs[0].score").value(0.9))
                .andExpect(jsonPath("$.data.songs[1].song.id").value(creepId))
                .andExpect(jsonPath("$.data.songs[1].song.difficulty[0].part").value("BASS"))
                .andExpect(jsonPath("$.data.songs[0].reasons").doesNotExist());

        ArgumentCaptor<AiSongRecommendRequest> captor = ArgumentCaptor.forClass(AiSongRecommendRequest.class);
        verify(aiSongRecommendationClient).recommend(captor.capture());
        assertThat(captor.getValue().mode()).isEqualTo("PERSONAL");
        assertThat(captor.getValue().genres()).containsExactly(Genre.POP);
        assertThat(captor.getValue().members())
                .containsExactly(new AiSongRecommendRequest.Member(Instrument.BASS, Level.INTERMEDIATE));
        assertThat(captor.getValue().limit()).isEqualTo(10);
    }

    @Test
    @DisplayName("내 취향 추천곡: 온보딩 전 사용자는 AI를 부르지 않고 빈 목록으로 200이다")
    void recommendForMe_notOnboarded_returnsEmpty() throws Exception {
        recommendForMe(memberId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.songs").isEmpty());

        verify(aiSongRecommendationClient, never()).recommend(any());
    }

    @Test
    @DisplayName("내 취향 추천곡: 온보딩한 사용자인데 AI 주소가 없으면 503, 인증이 없으면 401이다")
    void recommendForMe_errors() throws Exception {
        User leader = userRepository.findById(leaderId).orElseThrow();
        leader.completeOnboarding("팀장", Region.SEOUL, List.of(Genre.ROCK));
        userRepository.saveAndFlush(leader);
        given(aiSongRecommendationClient.isConfigured()).willReturn(false);

        recommendForMe(leaderId)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SONG_RECOMMENDATION_UNAVAILABLE"));
        mockMvc.perform(get("/v1/users/me/songs/recommendations"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions recommendForMe(long userId) throws Exception {
        return mockMvc.perform(get("/v1/users/me/songs/recommendations").header("Authorization", bearer(userId)));
    }

    private ResultActions recommend(long userId) throws Exception {
        return mockMvc.perform(get("/v1/teams/" + teamId + "/songs/recommendations")
                .header("Authorization", bearer(userId)));
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
