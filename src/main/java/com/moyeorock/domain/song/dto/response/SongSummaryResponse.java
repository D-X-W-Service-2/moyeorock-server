package com.moyeorock.domain.song.dto.response;

import com.moyeorock.domain.song.entity.Song;

// 곡 축약형 — 팀 선택곡·셋리스트 항목이 쓴다(dto-naming §10). 곡 검색·추천은 전체 정보 SongResponse를 쓴다.
public record SongSummaryResponse(Long id, String title, String artist) {

    public static SongSummaryResponse from(Song song) {
        return new SongSummaryResponse(song.getId(), song.getTitle(), song.getArtist());
    }
}
