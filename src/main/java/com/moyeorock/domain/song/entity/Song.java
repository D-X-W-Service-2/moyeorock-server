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

// 곡 마스터 데이터 — AI 서버가 자기 카탈로그의 곡 id(external_id)로 넣고 갱신한다(PUT /v1/internal/songs, 이슈 #147).
// 팀·사용자 데이터를 담지 않는다. 갱신해도 updated_at은 없다(erd.md §8).
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

    // DB 콜레이션은 utf8mb4_0900_bin(대소문자 구분, V13) — SongService의 Java 비교와 기준이 같다
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

    // AI 서버가 같은 externalId로 다시 보낸 곡 — 보낸 값으로 통째로 바꾼다(빠진 선택 값은 비워진다). externalId는 바꾸지 않는다.
    public void update(String title, String artist, Genre genre, String songKey, Integer bpm,
            Map<Instrument, Level> difficulty) {
        this.title = title;
        this.artist = artist;
        this.genre = genre;
        this.songKey = songKey;
        this.bpm = bpm;
        this.difficulty = difficulty;
    }
}
