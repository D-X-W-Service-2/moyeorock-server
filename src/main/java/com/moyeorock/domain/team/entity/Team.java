package com.moyeorock.domain.team.entity;

import com.moyeorock.global.common.entity.BaseEntity;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.domain.team.enums.TeamStatus;
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

    @Column(name = "performance_id")
    private Long performanceId;

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

    // 공연 내 팀 생성(dto-naming.md §9 PerformanceTeamCreateRequest, 7.1) — 4팀 performance
    // 도메인이 이 TeamService 메서드를 호출해서 만드는 팀. performanceId가 있으면 "공연 팀",
    // 없으면(위 create()) "독립 팀"이다(erd.md §12).
    public static Team createForPerformance(Long performanceId, String name, Region region) {
        Team team = create(name, region);
        team.performanceId = performanceId;
        return team;
    }

    public void disband() {
        this.status = TeamStatus.DISBANDED;
    }
}
