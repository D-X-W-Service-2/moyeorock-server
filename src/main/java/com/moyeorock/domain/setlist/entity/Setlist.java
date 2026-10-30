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

// 공연 셋리스트 — 공연 팀이 팀 선택곡(TeamSong) 중에서 넘긴 곡이 한 행이다(이슈 #85). 넘길 때 TeamSong에서
// 복사한다 — 넘긴 뒤 팀이 선택곡을 고쳐도 모임장이 보는 셋리스트가 바뀌면 안 되기 때문이다.
// 한 공연에 같은 곡을 두 번 넣을 수 없다 — UNIQUE(performance_id, song_id). 팀이 개별 해체되면 그 팀 행을 지워
// 다른 팀이 그 곡을 넘길 수 있게 한다(공연 종료로 인한 일괄 해체에서는 남긴다).
// 확정 여부는 이 테이블이 아니라 performances.setlist_confirmed_at에 둔다.
@Entity
@Table(name = "setlists")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Setlist extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "performance_id", nullable = false)
    private Long performanceId;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "song_id", nullable = false)
    private Long songId;

    // 모임장이 확정할 때 정한다. 확정 전에는 null
    @Column(name = "sort_order")
    private Integer sortOrder;

    public static Setlist create(Long performanceId, Long teamId, Long songId) {
        Setlist setlist = new Setlist();
        setlist.performanceId = performanceId;
        setlist.teamId = teamId;
        setlist.songId = songId;
        return setlist;
    }
}
