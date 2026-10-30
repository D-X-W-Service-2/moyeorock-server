package com.moyeorock.domain.setlist.entity;

import com.moyeorock.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 팀 선택곡 — 독립 팀·공연 팀 모두 고른 곡이 한 행이다(이슈 #85). 팀 쪽에서는 "셋리스트"라고 부르지 않는다.
// 공연 셋리스트는 Setlist가 따로 갖는다. setlist 도메인이 두 테이블을 모두 소유한다(domains.md "헷갈리는 경계").
//
// performanceId는 teams.performance_id를 복사해 둔 값이다 — setlist 도메인은 teams를 직접 조회할 수 없어서
// "같은 공연의 다른 팀이 고른 곡"을 찾으려면 필요하다. 팀은 공연을 옮기지 않으므로 값이 어긋나지 않는다.
// 같은 팀이 같은 곡을 두 번 고를 수 없다 — UNIQUE(team_id, song_id).
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

    // 독립 팀이면 null
    @Column(name = "performance_id")
    private Long performanceId;

    @Column(name = "song_id", nullable = false)
    private Long songId;

    public static TeamSong create(Long teamId, Long performanceId, Long songId) {
        TeamSong teamSong = new TeamSong();
        teamSong.teamId = teamId;
        teamSong.performanceId = performanceId;
        teamSong.songId = songId;
        return teamSong;
    }
}
