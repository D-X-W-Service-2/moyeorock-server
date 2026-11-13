package com.moyeorock.domain.song.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moyeorock.domain.song.client.AiSongClient;
import com.moyeorock.domain.song.client.AiSongPage;
import com.moyeorock.domain.song.dto.response.SongDifficultyResponse;
import com.moyeorock.domain.song.dto.response.SongResponse;
import com.moyeorock.domain.song.dto.response.SongSummaryResponse;
import com.moyeorock.domain.song.dto.response.SongSyncResponse;
import com.moyeorock.domain.song.entity.Song;
import com.moyeorock.domain.song.repository.SongRepository;
import com.moyeorock.domain.song.service.SongService.SyncResult;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

// AI 서버·DB 없이 확인한다. 트랜잭션은 목 PlatformTransactionManager로 페이지마다 커밋되는지만 센다. 검증은 실제 Validator를 쓴다.
@ExtendWith(MockitoExtension.class)
class SongServiceTest {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();
    // 수동 동기화를 부르는 사용자
    private static final long USER_ID = 7L;

    @Mock
    private SongRepository songRepository;
    @Mock
    private AiSongClient aiSongClient;
    @Mock
    private PlatformTransactionManager transactionManager;

    private SongService songService;

    @BeforeEach
    void setUp() {
        songService = new SongService(songRepository, aiSongClient, new TransactionTemplate(transactionManager), VALIDATOR);
    }

    @AfterAll
    static void closeValidator() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    @DisplayName("keyword의 앞뒤 공백을 지우고, 클라이언트 sort는 버리고 page·size만 넘긴다")
    void search_stripsKeyword_andDropsClientSort() {
        when(songRepository.search(any(), any(), any())).thenReturn(Page.empty());

        songService.search("  Queen  ", Genre.ROCK, PageRequest.of(1, 10, Sort.by("title")));

        verify(songRepository).search("Queen", Genre.ROCK, PageRequest.of(1, 10));
    }

    @Test
    @DisplayName("keyword가 비어 있으면 조건 없이(null) 검색한다")
    void search_treatsBlankKeywordAsNull() {
        when(songRepository.search(any(), any(), any())).thenReturn(Page.empty());

        songService.search("   ", null, PageRequest.of(0, 20));

        verify(songRepository).search(null, null, PageRequest.of(0, 20));
    }

    @Test
    @DisplayName("난이도는 [{part, level}] 배열로 펼치고 Instrument 순서로 정렬한다 — 정보가 없으면 빈 목록이다")
    void search_mapsDifficultyToOrderedList() {
        Map<Instrument, Level> difficulty = new LinkedHashMap<>();
        difficulty.put(Instrument.DRUM, Level.INTERMEDIATE);
        difficulty.put(Instrument.VOCAL, Level.ADVANCED);
        Song withDifficulty = Song.create("Bohemian Rhapsody", "Queen", Genre.ROCK, "Bb", 72, difficulty, null);
        Song withoutDifficulty = Song.create("Creep", "Radiohead", Genre.ROCK, null, null, null, null);
        when(songRepository.search(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(withDifficulty, withoutDifficulty), PageRequest.of(0, 20), 2));

        PageResponse<SongResponse> page = songService.search(null, null, PageRequest.of(0, 20));

        assertThat(page.content().get(0).difficulty()).containsExactly(
                new SongDifficultyResponse(Instrument.VOCAL, Level.ADVANCED),
                new SongDifficultyResponse(Instrument.DRUM, Level.INTERMEDIATE));
        assertThat(page.content().get(1).difficulty()).isEmpty();
    }

    @Test
    @DisplayName("곡 축약형 일괄 조회는 있는 곡만 id → 요약으로 돌려준다")
    void getSummaries_returnsExistingSongsOnly() {
        Song song = Song.create("Creep", "Radiohead", Genre.ROCK, null, null, null, null);
        ReflectionTestUtils.setField(song, "id", 104L);
        when(songRepository.findAllById(List.of(104L, 999L))).thenReturn(List.of(song));

        assertThat(songService.getSummaries(List.of(104L, 999L)))
                .containsExactly(Map.entry(104L, new SongSummaryResponse(104L, "Creep", "Radiohead")));
    }

    @Test
    @DisplayName("곡 id가 없으면 조회하지 않고 빈 Map을 돌려준다")
    void getSummaries_returnsEmpty_whenNoIds() {
        assertThat(songService.getSummaries(List.of())).isEmpty();
        verifyNoInteractions(songRepository);
    }

    @Test
    @DisplayName("externalId 일괄 조회는 우리 DB에 있는 곡만 externalId → 곡 전체 정보로 돌려준다(이슈 #171)")
    void findByExternalIds_returnsExistingSongsOnly() {
        Song song = Song.create("Creep", "Radiohead", Genre.ROCK, "G", 92, Map.of(Instrument.VOCAL, Level.NOVICE),
                "ext-1");
        ReflectionTestUtils.setField(song, "id", 104L);
        when(songRepository.findAllByExternalIdIn(List.of("ext-1", "ext-missing"))).thenReturn(List.of(song));

        Map<String, SongResponse> result = songService.findByExternalIds(List.of("ext-1", "ext-missing"));

        assertThat(result).containsOnlyKeys("ext-1");
        assertThat(result.get("ext-1")).isEqualTo(SongResponse.from(song));
    }

    @Test
    @DisplayName("externalId가 없으면 조회하지 않고 빈 Map을 돌려준다")
    void findByExternalIds_returnsEmpty_whenNoIds() {
        assertThat(songService.findByExternalIds(List.of())).isEmpty();
        verifyNoInteractions(songRepository);
    }

    @Test
    @DisplayName("동기화는 없는 externalId는 새로 만들고, 있는 externalId는 받은 값으로 통째로 바꾼다")
    void syncFromAi_createsNew_andReplacesExisting() {
        Song existing = Song.create("옛 제목", "옛 가수", Genre.POP, "C", 100,
                Map.of(Instrument.BASS, Level.BEGINNER), "ext-1");
        configured();
        when(aiSongClient.fetchSongs(null, SongService.SYNC_PAGE_SIZE)).thenReturn(page(null,
                item("ext-1", "Bohemian Rhapsody", List.of(new AiSongPage.Difficulty(Instrument.VOCAL, Level.ADVANCED))),
                item("ext-2", "Creep", null)));
        when(songRepository.findAllByExternalIdIn(List.of("ext-1", "ext-2"))).thenReturn(List.of(existing));

        SyncResult result = songService.syncFromAi();

        assertThat(result).isEqualTo(new SyncResult(1, 1, 1, 0));
        assertThat(existing.getTitle()).isEqualTo("Bohemian Rhapsody");
        assertThat(existing.getGenre()).isEqualTo(Genre.ROCK);
        assertThat(existing.getSongKey()).isNull();
        assertThat(existing.getDifficulty()).containsExactly(Map.entry(Instrument.VOCAL, Level.ADVANCED));
        assertThat(existing.getExternalId()).isEqualTo("ext-1");
        assertThat(savedSongs()).singleElement().satisfies(song -> {
            assertThat(song.getExternalId()).isEqualTo("ext-2");
            assertThat(song.getTitle()).isEqualTo("Creep");
            assertThat(song.getDifficulty()).isNull();
        });
    }

    @Test
    @DisplayName("nextCursor가 null이 될 때까지 받은 커서로 다음 페이지를 부르고, 페이지마다 따로 커밋한다")
    void syncFromAi_followsCursor_andCommitsEachPage() {
        configured();
        when(aiSongClient.fetchSongs(null, SongService.SYNC_PAGE_SIZE)).thenReturn(page("c1", item("ext-1", "A", null)));
        when(aiSongClient.fetchSongs("c1", SongService.SYNC_PAGE_SIZE)).thenReturn(page(null, item("ext-2", "B", null)));
        when(songRepository.findAllByExternalIdIn(any())).thenReturn(List.of());

        assertThat(songService.syncFromAi()).isEqualTo(new SyncResult(2, 2, 0, 0));
        verify(transactionManager, times(2)).commit(any());
    }

    @Test
    @DisplayName("검증에 걸린 곡(빈 곡·필수 값 누락·앞뒤 공백·같은 파트 중복·같은 페이지 안 externalId 중복)만 건너뛰고 나머지는 저장한다")
    void syncFromAi_skipsInvalidSongs() {
        configured();
        when(aiSongClient.fetchSongs(null, SongService.SYNC_PAGE_SIZE)).thenReturn(page(null,
                item("ext-1", "A", null),
                null,
                item("ext-2", " ", null),
                item(" ext-3", "C", null),
                item("ext-4", "D", List.of(new AiSongPage.Difficulty(Instrument.DRUM, Level.NOVICE),
                        new AiSongPage.Difficulty(Instrument.DRUM, Level.ADVANCED))),
                item("ext-1", "A 다시", null)));
        when(songRepository.findAllByExternalIdIn(List.of("ext-1"))).thenReturn(List.of());

        assertThat(songService.syncFromAi()).isEqualTo(new SyncResult(1, 1, 0, 5));
        assertThat(savedSongs()).extracting(Song::getTitle).containsExactly("A");
    }

    @Test
    @DisplayName("한 페이지가 모두 검증에 걸리면 DB를 건드리지 않고 다음 페이지로 넘어간다")
    void syncFromAi_skipsDb_whenWholePageInvalid() {
        configured();
        when(aiSongClient.fetchSongs(null, SongService.SYNC_PAGE_SIZE)).thenReturn(page(null, item("ext-1", "", null)));

        assertThat(songService.syncFromAi()).isEqualTo(new SyncResult(1, 0, 0, 1));
        verifyNoInteractions(songRepository, transactionManager);
    }

    @Test
    @DisplayName("뒤 페이지에서 AI 서버 호출이 실패하면 예외로 끝나고, 앞 페이지는 이미 커밋되어 남는다")
    void syncFromAi_keepsEarlierPages_whenLaterPageFails() {
        configured();
        when(aiSongClient.fetchSongs(null, SongService.SYNC_PAGE_SIZE)).thenReturn(page("c1", item("ext-1", "A", null)));
        when(aiSongClient.fetchSongs("c1", SongService.SYNC_PAGE_SIZE))
                .thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY));
        when(songRepository.findAllByExternalIdIn(any())).thenReturn(List.of());

        assertThatThrownBy(() -> songService.syncFromAi()).isInstanceOf(HttpServerErrorException.class);
        verify(transactionManager, times(1)).commit(any());
        assertThat(savedSongs()).extracting(Song::getExternalId).containsExactly("ext-1");
    }

    @Test
    @DisplayName("AI 서버가 같은 커서를 다시 돌려주면 무한히 돌지 않고 예외로 끝낸다")
    void syncFromAi_throws_whenCursorRepeats() {
        configured();
        when(aiSongClient.fetchSongs(null, SongService.SYNC_PAGE_SIZE)).thenReturn(page("c1"));
        when(aiSongClient.fetchSongs("c1", SongService.SYNC_PAGE_SIZE)).thenReturn(page("c1"));

        assertThatThrownBy(() -> songService.syncFromAi())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("같은 커서");
    }

    @Test
    @DisplayName("AI 서버 주소가 없으면 부르지 않고 NOT_RUN이다")
    void syncFromAi_notRun_whenNotConfigured() {
        when(aiSongClient.isConfigured()).thenReturn(false);

        assertThat(songService.syncFromAi()).isEqualTo(SyncResult.NOT_RUN);
        verify(aiSongClient, never()).fetchSongs(any(), anyInt());
    }

    @Test
    @DisplayName("이미 동기화가 돌고 있으면 새 실행은 AI 서버를 부르지 않고 NOT_RUN이다. 끝나면 다시 돌 수 있다")
    void syncFromAi_notRun_whileAlreadyRunning() {
        configured();
        List<SyncResult> nested = new ArrayList<>();
        when(aiSongClient.fetchSongs(isNull(), anyInt())).thenAnswer(invocation -> {
            nested.add(songService.syncFromAi()); // 첫 실행이 도는 도중에 들어온 두 번째 실행
            return page(null);
        });

        songService.syncFromAi();

        assertThat(nested).containsExactly(SyncResult.NOT_RUN);
        verify(aiSongClient, times(1)).fetchSongs(any(), anyInt());
        songService.syncFromAi();
        verify(aiSongClient, times(2)).fetchSongs(any(), anyInt());
    }

    @Test
    @DisplayName("songKey가 공백뿐이면 null, 앞뒤 공백은 지우고, 난이도가 빈 배열이면 null로 저장한다")
    void syncFromAi_normalizesSongKey_andEmptyDifficulty() {
        configured();
        when(aiSongClient.fetchSongs(null, SongService.SYNC_PAGE_SIZE)).thenReturn(page(null,
                new AiSongPage.Item("ext-1", "A", "Queen", Genre.ROCK, "   ", null, List.of()),
                new AiSongPage.Item("ext-2", "B", "Queen", Genre.ROCK, " Bb ", null, null)));
        when(songRepository.findAllByExternalIdIn(any())).thenReturn(List.of());

        songService.syncFromAi();

        List<Song> saved = savedSongs();
        assertThat(saved).extracting(Song::getSongKey).containsExactly(null, "Bb");
        assertThat(saved).extracting(Song::getDifficulty).containsOnlyNulls();
    }

    @Test
    @DisplayName("스케줄 실행은 동기화가 실패해도 예외를 밖으로 내지 않는다(다음 회차에 다시 돈다)")
    void syncFromAiOnSchedule_swallowsFailure() {
        configured();
        when(aiSongClient.fetchSongs(any(), anyInt())).thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

        songService.syncFromAiOnSchedule();

        verify(aiSongClient).fetchSongs(null, SongService.SYNC_PAGE_SIZE);
    }

    @Test
    @DisplayName("수동 동기화는 바로 돌고 페이지·생성·갱신·건너뜀 수를 돌려준다")
    void syncFromAiManually_returnsCounts() {
        configured();
        when(aiSongClient.fetchSongs(null, SongService.SYNC_PAGE_SIZE)).thenReturn(page("c1",
                item("ext-1", "A", null), item(" ext-2", "B", null)));
        when(aiSongClient.fetchSongs("c1", SongService.SYNC_PAGE_SIZE)).thenReturn(page(null, item("ext-3", "C", null)));
        when(songRepository.findAllByExternalIdIn(List.of("ext-1")))
                .thenReturn(List.of(Song.create("옛 제목", "Queen", Genre.ROCK, null, null, null, "ext-1")));
        when(songRepository.findAllByExternalIdIn(List.of("ext-3"))).thenReturn(List.of());

        SongSyncResponse response = songService.syncFromAiManually(USER_ID);

        assertThat(response).isEqualTo(new SongSyncResponse(2, 1, 1, 1));
    }

    @Test
    @DisplayName("AI 서버 주소가 없으면 수동 동기화는 503 SONG_SYNC_UNAVAILABLE이다")
    void syncFromAiManually_unavailable_whenNotConfigured() {
        when(aiSongClient.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> songService.syncFromAiManually(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SONG_SYNC_UNAVAILABLE);
        verify(aiSongClient, never()).fetchSongs(any(), anyInt());
    }

    @Test
    @DisplayName("동기화가 이미 돌고 있으면 수동 동기화는 409 SONG_SYNC_IN_PROGRESS이고 AI 서버를 다시 부르지 않는다")
    void syncFromAiManually_conflict_whileAlreadyRunning() {
        configured();
        List<Throwable> nested = new ArrayList<>();
        when(aiSongClient.fetchSongs(isNull(), anyInt())).thenAnswer(invocation -> {
            try {
                songService.syncFromAiManually(USER_ID); // 스케줄 실행이 도는 도중에 들어온 수동 실행
            } catch (BusinessException e) {
                nested.add(e);
            }
            return page(null);
        });

        songService.syncFromAi();

        assertThat(nested).singleElement().extracting("errorCode").isEqualTo(ErrorCode.SONG_SYNC_IN_PROGRESS);
        verify(aiSongClient, times(1)).fetchSongs(any(), anyInt());
    }

    @Test
    @DisplayName("AI 서버 호출이 실패하면 수동 동기화는 503이고, 앞 페이지는 남으며, 끝난 뒤 다시 부를 수 있다")
    void syncFromAiManually_unavailable_whenAiFails_andCanRunAgain() {
        configured();
        when(aiSongClient.fetchSongs(null, SongService.SYNC_PAGE_SIZE))
                .thenReturn(page("c1", item("ext-1", "A", null)))
                .thenReturn(page(null));
        when(aiSongClient.fetchSongs("c1", SongService.SYNC_PAGE_SIZE))
                .thenThrow(new ResourceAccessException("read timed out"));
        when(songRepository.findAllByExternalIdIn(any())).thenReturn(List.of());

        assertThatThrownBy(() -> songService.syncFromAiManually(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SONG_SYNC_UNAVAILABLE);
        verify(transactionManager, times(1)).commit(any());

        assertThat(songService.syncFromAiManually(USER_ID)).isEqualTo(new SongSyncResponse(1, 0, 0, 0));
    }

    @Test
    @DisplayName("AI 서버가 같은 커서를 다시 돌려주면(응답 형식 위반) 수동 동기화는 503이다")
    void syncFromAiManually_unavailable_whenCursorRepeats() {
        configured();
        when(aiSongClient.fetchSongs(null, SongService.SYNC_PAGE_SIZE)).thenReturn(page("c1"));
        when(aiSongClient.fetchSongs("c1", SongService.SYNC_PAGE_SIZE)).thenReturn(page("c1"));

        assertThatThrownBy(() -> songService.syncFromAiManually(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SONG_SYNC_UNAVAILABLE);
    }

    private void configured() {
        when(aiSongClient.isConfigured()).thenReturn(true);
    }

    @SuppressWarnings("unchecked")
    private List<Song> savedSongs() {
        ArgumentCaptor<List<Song>> captor = ArgumentCaptor.forClass(List.class);
        verify(songRepository, atLeastOnce()).saveAll(captor.capture());
        return captor.getAllValues().stream().flatMap(List::stream).toList();
    }

    private static AiSongPage page(String nextCursor, AiSongPage.Item... items) {
        return new AiSongPage(Arrays.asList(items), nextCursor);
    }

    // 장르 ROCK, 아티스트 고정, 키·BPM 없음 — 갱신 테스트에서 "빠진 선택 값은 비워진다"를 확인하려고 비워 둔다.
    private static AiSongPage.Item item(String externalId, String title, List<AiSongPage.Difficulty> difficulty) {
        return new AiSongPage.Item(externalId, title, "Queen", Genre.ROCK, null, null, difficulty);
    }
}
