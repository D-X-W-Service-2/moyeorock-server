package com.moyeorock.domain.song.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moyeorock.domain.song.dto.request.SongsUpsertRequest;
import com.moyeorock.domain.song.dto.response.SongDifficultyResponse;
import com.moyeorock.domain.song.dto.response.SongResponse;
import com.moyeorock.domain.song.dto.response.SongSummaryResponse;
import com.moyeorock.domain.song.dto.response.SongsUpsertResultResponse;
import com.moyeorock.domain.song.entity.Song;
import com.moyeorock.domain.song.repository.SongRepository;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SongServiceTest {

    @Mock
    private SongRepository songRepository;

    @InjectMocks
    private SongService songService;

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
    @DisplayName("일괄 등록·갱신은 없는 externalId는 새로 만들고, 있는 externalId는 보낸 값으로 통째로 바꾼다")
    void upsertAll_createsNew_andReplacesExisting() {
        Song existing = Song.create("옛 제목", "옛 가수", Genre.POP, "C", 100,
                Map.of(Instrument.BASS, Level.BEGINNER), "ext-1");
        when(songRepository.findAllByExternalIdIn(List.of("ext-1", "ext-2"))).thenReturn(List.of(existing));

        SongsUpsertResultResponse response = songService.upsertAll(new SongsUpsertRequest(List.of(
                item("ext-1", "Bohemian Rhapsody", List.of(
                        new SongsUpsertRequest.Difficulty(Instrument.VOCAL, Level.ADVANCED))),
                item("ext-2", "Creep", null))));

        assertThat(response).isEqualTo(new SongsUpsertResultResponse(1, 1));
        assertThat(existing.getTitle()).isEqualTo("Bohemian Rhapsody");
        assertThat(existing.getGenre()).isEqualTo(Genre.ROCK);
        assertThat(existing.getSongKey()).isNull();
        assertThat(existing.getDifficulty()).containsExactly(Map.entry(Instrument.VOCAL, Level.ADVANCED));
        assertThat(existing.getExternalId()).isEqualTo("ext-1");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Song>> saved = ArgumentCaptor.forClass(List.class);
        verify(songRepository).saveAllAndFlush(saved.capture());
        assertThat(saved.getValue()).singleElement().satisfies(song -> {
            assertThat(song.getExternalId()).isEqualTo("ext-2");
            assertThat(song.getTitle()).isEqualTo("Creep");
            assertThat(song.getDifficulty()).isNull();
        });
    }

    @Test
    @DisplayName("한 요청 안에 같은 externalId가 있으면 몇 번째 곡인지 담아 VALIDATION_FAILED를 던지고 조회·저장하지 않는다")
    void upsertAll_throws_whenExternalIdDuplicated() {
        SongsUpsertRequest request = new SongsUpsertRequest(List.of(
                item("ext-1", "A", null), item("ext-2", "B", null), item("ext-1", "C", null)));

        assertThatThrownBy(() -> songService.upsertAll(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageStartingWith("songs[2].externalId:")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(songRepository);
    }

    @Test
    @DisplayName("한 곡의 난이도에 같은 파트가 두 번 있으면 몇 번째 곡인지 담아 VALIDATION_FAILED를 던지고 조회·저장하지 않는다")
    void upsertAll_throws_whenDifficultyPartDuplicated() {
        SongsUpsertRequest request = new SongsUpsertRequest(List.of(
                item("ext-1", "A", null),
                item("ext-2", "B", List.of(
                        new SongsUpsertRequest.Difficulty(Instrument.DRUM, Level.NOVICE),
                        new SongsUpsertRequest.Difficulty(Instrument.DRUM, Level.ADVANCED)))));

        assertThatThrownBy(() -> songService.upsertAll(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageStartingWith("songs[1].difficulty:")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(songRepository);
    }

    @Test
    @DisplayName("새 곡 저장이 유니크 제약에 걸리면(같은 새 externalId의 동시 요청) 500이 아니라 INVALID_STATE를 던진다")
    void upsertAll_throwsInvalidState_whenConcurrentInsertCollides() {
        when(songRepository.findAllByExternalIdIn(List.of("ext-1"))).thenReturn(List.of());
        when(songRepository.saveAllAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_songs_external_id"));

        assertThatThrownBy(() -> songService.upsertAll(new SongsUpsertRequest(List.of(item("ext-1", "A", null)))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    @DisplayName("songKey가 공백뿐이면 키 정보 없음(null)으로, 앞뒤 공백이 있으면 지우고 저장한다")
    void upsertAll_normalizesSongKey() {
        when(songRepository.findAllByExternalIdIn(List.of("ext-1", "ext-2"))).thenReturn(List.of());

        songService.upsertAll(new SongsUpsertRequest(List.of(
                new SongsUpsertRequest.Item("ext-1", "A", "Queen", Genre.ROCK, "   ", null, null),
                new SongsUpsertRequest.Item("ext-2", "B", "Queen", Genre.ROCK, " Bb ", null, null))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Song>> saved = ArgumentCaptor.forClass(List.class);
        verify(songRepository).saveAllAndFlush(saved.capture());
        assertThat(saved.getValue()).extracting(Song::getSongKey).containsExactly(null, "Bb");
    }

    @Test
    @DisplayName("난이도가 빈 배열이면 정보 없음(null)으로 저장한다")
    void upsertAll_storesNullDifficulty_whenEmpty() {
        when(songRepository.findAllByExternalIdIn(List.of("ext-1"))).thenReturn(List.of());

        songService.upsertAll(new SongsUpsertRequest(List.of(item("ext-1", "A", List.of()))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Song>> saved = ArgumentCaptor.forClass(List.class);
        verify(songRepository).saveAllAndFlush(saved.capture());
        assertThat(saved.getValue().get(0).getDifficulty()).isNull();
    }

    // 장르 ROCK, 아티스트 고정, 키·BPM 없음 — 갱신 테스트에서 "빠진 선택 값은 비워진다"를 확인하려고 비워 둔다.
    private static SongsUpsertRequest.Item item(String externalId, String title,
            List<SongsUpsertRequest.Difficulty> difficulty) {
        return new SongsUpsertRequest.Item(externalId, title, "Queen", Genre.ROCK, null, null, difficulty);
    }
}
