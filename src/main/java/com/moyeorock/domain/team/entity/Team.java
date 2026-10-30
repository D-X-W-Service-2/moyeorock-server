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

    public static Team create(String name, String description, Region region) {
        Team team = new Team();
        team.name = name;
        team.description = description;
        team.region = region;
        team.status = TeamStatus.ACTIVE;
        return team;
    }

    // 공연 내 팀 생성(dto-naming.md §9 PerformanceTeamCreateRequest, 7.1) — 4팀 performance
    // 도메인이 이 TeamService 메서드를 호출해서 만드는 팀. performanceId가 있으면 "공연 팀",
    // 없으면(위 create()) "독립 팀"이다(erd.md §12).
    public static Team createForPerformance(Long performanceId, String name, String description, Region region) {
        Team team = create(name, description, region);
        team.performanceId = performanceId;
        return team;
    }

    // 팀 정보 수정(PUT, 전체 교체). status는 여기서 바꾸지 않는다 — 해체는 disband()로만 (dto-spec §2 TeamUpdateRequest).
    public void update(String name, String description, Region region) {
        this.name = name;
        this.description = description;
        this.region = region;
    }

    // 팀을 해제 상태로 바꾸는 메서드 soft delete
    public void disband() {
        this.status = TeamStatus.DISBANDED;
    }
}
