package com.moyeorock.domain.song.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.song.entity.Song;
import com.moyeorock.domain.song.repository.SongRepository;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import com.moyeorock.global.security.InternalApiKeyFilter;
import com.moyeorock.global.security.JwtProvider;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 서버 곡 일괄 등록·갱신(PUT /v1/internal/songs, 이슈 #147)을 실제 HTTP·보안 필터·MySQL로 확인한다.
 * 키는 test application.yml의 {@code internal.api-key}다. 클래스 단위 {@code @Transactional}이라 테스트마다 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class SongUpsertApiTest {

    private static final String PATH = "/v1/internal/songs";

    @Value("${internal.api-key}")
    private String apiKey;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private SongRepository songRepository;

    @Test
    @DisplayName("API 키가 없거나 틀리면 401이고, 사용자 JWT만으로도 401이다 — 아무것도 저장되지 않는다")
    void withoutValidKey_returns401() throws Exception {
        String body = body(song("ext-1", "Creep", "ROCK", null));

        upsert(put(PATH), body).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        upsert(put(PATH).header(InternalApiKeyFilter.HEADER, "wrong-key"), body).andExpect(status().isUnauthorized());
        upsert(put(PATH).header("Authorization", "Bearer " + jwtProvider.generateToken(1L)), body)
                .andExpect(status().isUnauthorized());

        assertThat(songRepository.findAllByExternalIdIn(List.of("ext-1"))).isEmpty();
    }

    @Test
    @DisplayName("API 키로는 화면용 API(곡 검색)를 부를 수 없다 — 401")
    void apiKey_doesNotOpenUserApi() throws Exception {
        mockMvc.perform(get("/v1/songs").header(InternalApiKeyFilter.HEADER, apiKey)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("새 곡은 만들고, 다시 보내면 같은 곡(id 그대로)을 보낸 값으로 바꾸며, 넣은 곡은 곡 검색에 나온다")
    void upsert_createsThenUpdates_andSongsAreSearchable() throws Exception {
        upsert(withKey(), body(
                song("ext-1", "Bohemian Rhapsody", "ROCK", "[{\"part\":\"VOCAL\",\"level\":\"ADVANCED\"}]"),
                song("ext-2", "Creep", "ROCK", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(2))
                .andExpect(jsonPath("$.data.updated").value(0));
        Map<String, Long> idsBefore = idsByExternalId();

        upsert(withKey(), body(
                song("ext-1", "Bohemian Rhapsody (Remastered)", "ROCK",
                        "[{\"part\":\"VOCAL\",\"level\":\"ADVANCED\"},{\"part\":\"DRUM\",\"level\":\"INTERMEDIATE\"}]"),
                song("ext-2", "Creep", "ROCK", null),
                song("ext-3", "Wonderwall", "POP", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(1))
                .andExpect(jsonPath("$.data.updated").value(2));

        Map<String, Long> idsAfter = idsByExternalId();
        assertThat(idsAfter).containsAllEntriesOf(idsBefore).containsKey("ext-3");
        Song updated = songRepository.findById(idsBefore.get("ext-1")).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("Bohemian Rhapsody (Remastered)");
        assertThat(updated.getDifficulty())
                .containsOnly(Map.entry(Instrument.VOCAL, Level.ADVANCED), Map.entry(Instrument.DRUM, Level.INTERMEDIATE));

        mockMvc.perform(get("/v1/songs?keyword=Remastered")
                        .header("Authorization", "Bearer " + jwtProvider.generateToken(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].artist").value("Queen"))
                .andExpect(jsonPath("$.data.content[0].genre").value("ROCK"))
                .andExpect(jsonPath("$.data.content[0].difficulty.length()").value(2));
    }

    @Test
    @DisplayName("요청에 없는 곡은 지우지 않는다")
    void upsert_keepsSongsMissingFromRequest() throws Exception {
        upsert(withKey(), body(song("ext-1", "A", "ROCK", null), song("ext-2", "B", "ROCK", null)))
                .andExpect(status().isOk());

        upsert(withKey(), body(song("ext-1", "A", "ROCK", null))).andExpect(status().isOk());

        assertThat(songRepository.findAllByExternalIdIn(List.of("ext-1", "ext-2"))).hasSize(2);
    }

    @Test
    @DisplayName("한 요청에 externalId가 겹치면 400이고 몇 번째 곡인지 알려주며, 아무것도 저장되지 않는다")
    void upsert_duplicateExternalId_returns400() throws Exception {
        upsert(withKey(), body(song("ext-1", "A", "ROCK", null), song("ext-1", "B", "ROCK", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("songs[1].externalId: 같은 요청 안에 같은 외부 곡 아이디가 있습니다."));

        assertThat(songRepository.findAllByExternalIdIn(List.of("ext-1"))).isEmpty();
    }

    @Test
    @DisplayName("필수 값이 빠지거나, 없는 장르이거나, 빈 목록·500곡 초과면 400이고 아무것도 저장되지 않는다")
    void upsert_invalidRequests_return400() throws Exception {
        upsert(withKey(), "{\"songs\":[{\"externalId\":\"ext-1\",\"artist\":\"Queen\",\"genre\":\"ROCK\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("songs[0].title: 곡 제목은 필수입니다."));
        upsert(withKey(), body(song("ext-1", "A", "TROT", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        upsert(withKey(), "{\"songs\":[]}").andExpect(status().isBadRequest());
        String tooMany = IntStream.rangeClosed(1, 501)
                .mapToObj(i -> song("ext-" + i, "곡" + i, "ROCK", null))
                .collect(Collectors.joining(",", "{\"songs\":[", "]}"));
        upsert(withKey(), tooMany).andExpect(status().isBadRequest());

        assertThat(songRepository.findAllByExternalIdIn(List.of("ext-1"))).isEmpty();
    }

    @Test
    @DisplayName("externalId는 대소문자를 구분한다 — 대소문자만 다른 id는 다른 곡으로 만들고, 다시 보내면 그 곡만 바꾼다")
    void upsert_treatsExternalIdCaseSensitively() throws Exception {
        upsert(withKey(), body(song("yt:abcDEF", "A", "ROCK", null), song("yt:abcdef", "B", "ROCK", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(2))
                .andExpect(jsonPath("$.data.updated").value(0));

        upsert(withKey(), body(song("yt:abcdef", "B (Live)", "ROCK", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(0))
                .andExpect(jsonPath("$.data.updated").value(1));

        Map<String, String> titles = songRepository.findAllByExternalIdIn(List.of("yt:abcDEF", "yt:abcdef")).stream()
                .collect(Collectors.toMap(Song::getExternalId, Song::getTitle));
        assertThat(titles).containsOnly(Map.entry("yt:abcDEF", "A"), Map.entry("yt:abcdef", "B (Live)"));
    }

    @Test
    @DisplayName("externalId 앞뒤에 공백이 있으면 400이고 몇 번째 곡인지 알려주며, 아무것도 저장되지 않는다")
    void upsert_externalIdWithSurroundingSpace_returns400() throws Exception {
        upsert(withKey(), body(song("ext-1", "A", "ROCK", null), song("ext-2 ", "B", "ROCK", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("songs[1].externalId: 외부 곡 아이디 앞뒤에 공백이 있으면 안 됩니다."));

        assertThat(songRepository.findAllByExternalIdIn(List.of("ext-1", "ext-2", "ext-2 "))).isEmpty();
    }

    @Test
    @DisplayName("songKey가 빈 문자열·공백뿐이면 키 정보 없음(null)으로 저장한다")
    void upsert_storesBlankSongKeyAsNull() throws Exception {
        upsert(withKey(), "{\"songs\":[{\"externalId\":\"ext-1\",\"title\":\"A\",\"artist\":\"Queen\",\"genre\":\"ROCK\","
                + "\"songKey\":\"  \"}]}")
                .andExpect(status().isOk());

        assertThat(songRepository.findAllByExternalIdIn(List.of("ext-1")))
                .singleElement().satisfies(song -> assertThat(song.getSongKey()).isNull());
    }

    private MockHttpServletRequestBuilder withKey() {
        return put(PATH).header(InternalApiKeyFilter.HEADER, apiKey);
    }

    private ResultActions upsert(MockHttpServletRequestBuilder builder, String body) throws Exception {
        return mockMvc.perform(builder.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String body(String... songs) {
        return "{\"songs\":[" + String.join(",", songs) + "]}";
    }

    private static String song(String externalId, String title, String genre, String difficultyJson) {
        return "{\"externalId\":\"%s\",\"title\":\"%s\",\"artist\":\"Queen\",\"genre\":\"%s\",\"difficulty\":%s}"
                .formatted(externalId, title, genre, difficultyJson);
    }

    private Map<String, Long> idsByExternalId() {
        return songRepository.findAllByExternalIdIn(List.of("ext-1", "ext-2", "ext-3")).stream()
                .collect(Collectors.toMap(Song::getExternalId, Song::getId));
    }
}
