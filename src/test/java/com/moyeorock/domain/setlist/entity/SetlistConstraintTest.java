package com.moyeorock.domain.setlist.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * V9·V10 마이그레이션의 유니크 제약이 실제 MySQL에서 걸리는지 본다. 팀 선택곡은 팀 단위, 공연 셋리스트는
 * 공연 단위로 같은 곡을 막는다. Repository는 셋리스트 기능 구현 때 만들므로 여기서는 EntityManager로 저장한다.
 */
@DataJpaTest
@Import({JpaAuditingConfig.class, TestcontainersConfig.class})
class SetlistConstraintTest {

    private static final Long PERFORMANCE_ID = 7L;
    private static final Long TEAM_A = 5L;
    private static final Long TEAM_B = 9L;
    private static final Long SONG = 88L;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("같은 팀이 같은 곡을 두 번 고르면 team_songs 유니크 제약에 걸린다")
    void teamSongs_rejectSameSongTwiceInSameTeam() {
        persistAndFlush(TeamSong.create(TEAM_A, PERFORMANCE_ID, SONG));

        assertThatThrownBy(() -> persistAndFlush(TeamSong.create(TEAM_A, PERFORMANCE_ID, SONG)))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("팀 선택곡은 같은 공연의 다른 팀이 같은 곡을 골라도 된다 — 공연 단위 중복은 셋리스트에서만 막는다")
    void teamSongs_allowSameSongInOtherTeamOfSamePerformance() {
        persistAndFlush(TeamSong.create(TEAM_A, PERFORMANCE_ID, SONG));
        persistAndFlush(TeamSong.create(TEAM_B, PERFORMANCE_ID, SONG));

        assertThat(countTeamSongs()).isEqualTo(2);
    }

    @Test
    @DisplayName("독립 팀(performance_id NULL)끼리는 같은 곡을 골라도 된다")
    void teamSongs_allowSameSongForIndependentTeams() {
        persistAndFlush(TeamSong.create(TEAM_A, null, SONG));
        persistAndFlush(TeamSong.create(TEAM_B, null, SONG));

        assertThat(countTeamSongs()).isEqualTo(2);
    }

    @Test
    @DisplayName("한 공연에 같은 곡을 다른 팀이 또 넘기면 setlists 유니크 제약에 걸린다")
    void setlists_rejectSameSongTwiceInSamePerformance() {
        persistAndFlush(Setlist.create(PERFORMANCE_ID, TEAM_A, SONG));

        assertThatThrownBy(() -> persistAndFlush(Setlist.create(PERFORMANCE_ID, TEAM_B, SONG)))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("다른 공연이면 같은 곡을 셋리스트에 넣을 수 있다")
    void setlists_allowSameSongInOtherPerformance() {
        persistAndFlush(Setlist.create(PERFORMANCE_ID, TEAM_A, SONG));
        persistAndFlush(Setlist.create(PERFORMANCE_ID + 1, TEAM_B, SONG));

        Long count = entityManager.createQuery("SELECT COUNT(s) FROM Setlist s", Long.class).getSingleResult();
        assertThat(count).isEqualTo(2);
    }

    private void persistAndFlush(Object entity) {
        entityManager.persist(entity);
        entityManager.flush();
    }

    private Long countTeamSongs() {
        return entityManager.createQuery("SELECT COUNT(t) FROM TeamSong t", Long.class).getSingleResult();
    }
}
