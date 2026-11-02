package com.moyeorock.domain.team.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.domain.team.enums.TeamStatus;
import com.moyeorock.global.common.enums.Region;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TeamTest {

    @Test
    @DisplayName("독립 팀은 ACTIVE 상태로 태어나고 공연에 소속되지 않는다")
    void create_startsActive_withoutPerformance() {
        Team team = Team.create("무명밴드", "합주 위주로 편하게 합니다.", Region.SEOUL);

        // assertThat(실제값.isEuqalTo(기댓값)) -> 다음 형태로 검증하는 함수
        assertThat(team.getName()).isEqualTo("무명밴드");
        assertThat(team.getDescription()).isEqualTo("합주 위주로 편하게 합니다.");
        assertThat(team.getRegion()).isEqualTo(Region.SEOUL);
        assertThat(team.getStatus()).isEqualTo(TeamStatus.ACTIVE);
        assertThat(team.getPerformanceId()).isNull();
    }

    @Test
    @DisplayName("공연 내 팀은 performanceId를 가진다")
    void createForPerformance_hasPerformanceId() {
        Team team = Team.createForPerformance(7L, "무명밴드", "발라드 위주로 갑니다.", Region.SEOUL);

        assertThat(team.getPerformanceId()).isEqualTo(7L);
        assertThat(team.getStatus()).isEqualTo(TeamStatus.ACTIVE);
    }

    @Test
    @DisplayName("update는 이름·소개·지역을 전체 교체하고 상태는 바꾸지 않는다")
    void update_replacesFields_keepsStatus() {
        Team team = Team.create("무명밴드", "예전 소개", Region.SEOUL);

        team.update("유명밴드", null, null);

        assertThat(team.getName()).isEqualTo("유명밴드");
        assertThat(team.getDescription()).isNull();
        assertThat(team.getRegion()).isNull();
        assertThat(team.getStatus()).isEqualTo(TeamStatus.ACTIVE);
    }

    @Test
    @DisplayName("disband는 상태를 DISBANDED로 바꾼다")
    void disband_changesStatusToDisbanded() {
        Team team = Team.create("무명밴드", null, Region.SEOUL);

        team.disband();

        assertThat(team.getStatus()).isEqualTo(TeamStatus.DISBANDED);
    }
}
