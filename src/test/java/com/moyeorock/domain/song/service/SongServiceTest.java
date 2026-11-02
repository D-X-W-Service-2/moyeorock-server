package com.moyeorock.domain.song.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moyeorock.domain.song.dto.response.SongDifficultyResponse;
import com.moyeorock.domain.song.dto.response.SongResponse;
import com.moyeorock.domain.song.entity.Song;
import com.moyeorock.domain.song.repository.SongRepository;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

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
}
