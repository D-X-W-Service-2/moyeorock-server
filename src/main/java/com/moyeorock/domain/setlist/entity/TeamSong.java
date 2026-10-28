package com.moyeorock.domain.setlist.entity;

import com.moyeorock.domain.setlist.enums.SongProgress;
import com.moyeorock.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// setlist 도메인이 team_songs를 소유한다(domains.md "헷갈리는 경계") — team·performance는
// 이 테이블을 직접 건드리지 않는다.
//
// erd.md §15의 2026-09-16 변경 이후 performance_id·selected_song_id·그 UNIQUE는 삭제됐다.
// "공연 내 곡 중복 방지"는 더 이상 DB가 강제하지 않고, progress를 SELECTED로 바꾸는 시점에
// 이 도메인의 Service가 같은 공연 소속 팀들의 team_songs를 조회해 애플리케이션에서 검증해야
// 한다(후속 구현 필요 — 엔티티만으로는 못 막는다).
@Entity
@Table(name = "team_songs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamSong extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "song_id", nullable = false)
    private Long songId;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private SongProgress progress;

    public static TeamSong create(Long teamId, Long songId, Integer sortOrder) {
        TeamSong teamSong = new TeamSong();
        teamSong.teamId = teamId;
        teamSong.songId = songId;
        teamSong.sortOrder = sortOrder;
        teamSong.progress = SongProgress.CANDIDATE;
        return teamSong;
    }

    public void changeProgress(SongProgress progress) {
        this.progress = progress;
    }

    public void reorder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
