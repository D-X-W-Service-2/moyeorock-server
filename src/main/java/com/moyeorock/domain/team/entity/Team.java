package com.moyeorock.domain.team.entity;

import com.moyeorock.global.common.entity.BaseEntity;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.domain.team.enums.TeamStatus;
// import com.moyeorock.domain.performance.entity.Performance;
// → 4팀의 Performance 엔티티가 아직 없어서 주석 처리. @ManyToOne 필드 자체는 미리 적어 두고,
//   Performance 클래스가 생기면 이 import와 아래 필드의 주석만 풀면 되게 해뒀다.
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

@Entity
@Table(name = "teams")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- performance 연관관계는 Performance 엔티티가 없어서 임시로 주석 처리 ---
    // @ManyToOne(fetch = LAZY): architecture.md §5 "연관관계는 전부 LAZY" 규칙.
    //   EAGER로 두면 Team을 조회할 때마다 필요 없어도 performances를 매번 조인해서 가져온다.
    // @JoinColumn(name = "performance_id"): FK 컬럼명을 ERD와 맞춤. nullable 기본값 true라
    //   "NULL = 독립 팀"이라는 ERD 규칙을 그대로 만족한다(따로 nullable=false 안 붙임).
    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "performance_id")
    // private Performance performance;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private Region region;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private TeamStatus status;

    public static Team create(String name, Region region) {
        Team team = new Team();
        team.name = name;
        team.region = region;
        team.status = TeamStatus.ACTIVE;
        return team;
    }

    public void disband() {
        this.status = TeamStatus.DISBANDED;
    }
}
