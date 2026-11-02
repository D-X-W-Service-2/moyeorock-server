package com.moyeorock.domain.setlist.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.setlist.entity.Setlist;
import com.moyeorock.global.config.JpaAuditingConfig;
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
}
