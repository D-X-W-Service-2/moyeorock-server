package com.moyeorock.domain.setlist.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.setlist.entity.Setlist;
import com.moyeorock.global.config.JpaAuditingConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({JpaAuditingConfig.class, TestcontainersConfig.class})
class SetlistRepositoryTest {

    @Autowired
    private SetlistRepository setlistRepository;

    @Test
    @DisplayName("팀 개별 해체용 삭제는 그 팀이 넘긴 셋리스트 행만 지운다")
    void deleteByTeamId_onlyDeletesTheTeamRows() {
        setlistRepository.save(Setlist.create(7L, 5L, 88L));
        setlistRepository.save(Setlist.create(7L, 5L, 91L));
        setlistRepository.saveAndFlush(Setlist.create(7L, 9L, 104L));

        setlistRepository.deleteByTeamId(5L);

        assertThat(setlistRepository.findAll()).extracting(Setlist::getTeamId).containsExactly(9L);
    }

    @Test
    @DisplayName("확정 전 셋리스트는 그 공연 행만 팀 id 순, 팀 안에서는 넘긴 순으로 준다")
    void findUnconfirmed_ordersByTeamThenSubmitOrder() {
        setlistRepository.save(Setlist.create(7L, 9L, 104L));
        setlistRepository.save(Setlist.create(7L, 5L, 91L));
        setlistRepository.save(Setlist.create(7L, 5L, 88L));
        setlistRepository.saveAndFlush(Setlist.create(8L, 5L, 88L));

        assertThat(setlistRepository.findAllByPerformanceIdOrderByTeamIdAscIdAsc(7L))
                .extracting(Setlist::getSongId).containsExactly(91L, 88L, 104L);
    }

    @Test
    @DisplayName("같은 공연의 다른 팀이 이미 넘긴 곡만 걸린다 — 자기 팀이 넘긴 곡이나 다른 공연은 아니다")
    void existsByOtherTeam_onlyMatchesOtherTeamsInSamePerformance() {
        setlistRepository.save(Setlist.create(7L, 5L, 88L));
        setlistRepository.saveAndFlush(Setlist.create(8L, 9L, 91L));

        assertThat(setlistRepository.existsByPerformanceIdAndTeamIdNotAndSongIdIn(7L, 9L, List.of(88L))).isTrue();
        assertThat(setlistRepository.existsByPerformanceIdAndTeamIdNotAndSongIdIn(7L, 5L, List.of(88L))).isFalse();
        assertThat(setlistRepository.existsByPerformanceIdAndTeamIdNotAndSongIdIn(7L, 5L, List.of(91L))).isFalse();
    }

    @Test
    @DisplayName("넘기기용 삭제는 그 공연에서 그 팀이 넘긴 행만 지우고, 지운 뒤 같은 곡을 다시 넣어도 유니크에 걸리지 않는다")
    void deleteByPerformanceAndTeam_thenResubmitSameSong() {
        setlistRepository.save(Setlist.create(7L, 5L, 88L));
        setlistRepository.save(Setlist.create(7L, 9L, 104L));
        setlistRepository.saveAndFlush(Setlist.create(8L, 5L, 91L));

        setlistRepository.deleteByPerformanceIdAndTeamId(7L, 5L);
        setlistRepository.saveAndFlush(Setlist.create(7L, 5L, 88L));

        assertThat(setlistRepository.findAllByPerformanceIdOrderByTeamIdAscIdAsc(7L))
                .extracting(Setlist::getTeamId, Setlist::getSongId)
                .containsExactly(tuple(5L, 88L), tuple(9L, 104L));
        assertThat(setlistRepository.findAllByPerformanceIdOrderByTeamIdAscIdAsc(8L)).hasSize(1);
    }
}
