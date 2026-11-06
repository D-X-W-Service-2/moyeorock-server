package com.moyeorock.domain.song.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.song.entity.Song;
import com.moyeorock.domain.song.repository.SongRepository;
import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.repository.UserRepository;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import com.moyeorock.global.security.JwtProvider;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 곡 검색의 보안 필터·쿼리 파라미터 변환·JSON 모양(난이도 배열)을 실제 HTTP 흐름으로 확인한다.
 * 곡을 넣는 API가 없어서 곡은 Repository로 직접 넣는다. 클래스 단위 {@code @Transactional}이라 테스트마다 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class SongApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SongRepository songRepository;

    private long userId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(User.signupWithEmail(
                "회원@moyeorock.test", "hash", "회원", LocalDateTime.now())).getId();
        songRepository.save(Song.create("Bohemian Rhapsody", "Queen", Genre.ROCK, "Bb", 72,
                Map.of(Instrument.VOCAL, Level.ADVANCED, Instrument.DRUM, Level.INTERMEDIATE), null));
        songRepository.save(Song.create("Creep", "Radiohead", Genre.ROCK, null, null, null, null));
    }

    @Test
    @DisplayName("토큰 없이 호출하면 401이다")
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/v1/songs")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("keyword로 찾은 곡을 페이지로 돌려주고, 난이도는 [{part, level}] 배열이다")
    void search_returnsSongsWithDifficultyArray() throws Exception {
        mockMvc.perform(get("/v1/songs?keyword=Queen&genre=ROCK").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Bohemian Rhapsody"))
                .andExpect(jsonPath("$.data.content[0].artist").value("Queen"))
                .andExpect(jsonPath("$.data.content[0].genre").value("ROCK"))
                .andExpect(jsonPath("$.data.content[0].songKey").value("Bb"))
                .andExpect(jsonPath("$.data.content[0].bpm").value(72))
                .andExpect(jsonPath("$.data.content[0].difficulty[0].part").value("VOCAL"))
                .andExpect(jsonPath("$.data.content[0].difficulty[0].level").value("ADVANCED"))
                .andExpect(jsonPath("$.data.content[0].difficulty[1].part").value("DRUM"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("난이도 정보가 없는 곡은 difficulty가 빈 배열이다")
    void search_returnsEmptyDifficulty_whenSongHasNone() throws Exception {
        mockMvc.perform(get("/v1/songs?keyword=Creep").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].difficulty").isEmpty());
    }

    @Test
    @DisplayName("없는 장르 값이면 400 VALIDATION_FAILED다")
    void search_withInvalidGenre_returns400() throws Exception {
        mockMvc.perform(get("/v1/songs?genre=KPOP").header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private String bearer() {
        return "Bearer " + jwtProvider.generateToken(userId);
    }
}
