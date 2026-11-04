package com.moyeorock.domain.song.dto.response;

import com.moyeorock.domain.song.entity.Song;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import java.util.List;
import java.util.Map;

/**
 * 곡 정보 전체 — 곡 검색·추천이 쓴다. 선택곡·셋리스트 항목에는 축약형 {@code SongSummaryResponse}를 쓴다(dto-naming §10).
 * {@code difficulty}는 정보가 있는 파트만 {@link Instrument} 순서대로 담고, 난이도 정보가 아예 없으면 빈 목록이다.
 */
public record SongResponse(
        Long id,
        String title,
        String artist,
        Genre genre,
        String songKey,
        Integer bpm,
        List<SongDifficultyResponse> difficulty
) {

    public static SongResponse from(Song song) {
        return new SongResponse(song.getId(), song.getTitle(), song.getArtist(), song.getGenre(), song.getSongKey(),
                song.getBpm(), toDifficulty(song.getDifficulty()));
    }

    // JSON 객체의 키 순서는 저장한 쪽에 따라 달라서, 응답 순서를 Instrument 선언 순으로 고정한다
    private static List<SongDifficultyResponse> toDifficulty(Map<Instrument, Level> difficulty) {
        if (difficulty == null) {
            return List.of();
        }
        return difficulty.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new SongDifficultyResponse(entry.getKey(), entry.getValue()))
                .toList();
    }
}
