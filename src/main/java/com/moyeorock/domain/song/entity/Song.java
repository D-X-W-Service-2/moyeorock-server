package com.moyeorock.domain.song.entity;

import com.moyeorock.global.common.entity.BaseTimeEntity;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 곡 마스터 데이터 — 외부 카탈로그(external_id)에서 가져온다. 팀·사용자 데이터를 담지 않으므로
// 생성 이후 수정 메서드가 없다(erd.md §8에 updated_at도 없음).
@Entity
@Table(name = "songs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Song extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 200)
    private String title;

    @Column(length = 100)
    private String artist;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Genre genre;

    @Column(name = "song_key", length = 10)
    private String songKey;

    private Integer bpm;

    @Convert(converter = SongDifficultyConverter.class)
    @Column(columnDefinition = "json")
    private Map<Instrument, Level> difficulty;

    @Column(name = "external_id", length = 100)
    private String externalId;

    public static Song create(String title, String artist, Genre genre, String songKey, Integer bpm,
            Map<Instrument, Level> difficulty, String externalId) {
        Song song = new Song();
        song.title = title;
        song.artist = artist;
        song.genre = genre;
        song.songKey = songKey;
        song.bpm = bpm;
        song.difficulty = difficulty;
        song.externalId = externalId;
        return song;
    }
}
