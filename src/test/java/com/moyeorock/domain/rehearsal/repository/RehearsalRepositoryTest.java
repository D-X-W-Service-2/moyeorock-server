package com.moyeorock.domain.rehearsal.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.rehearsal.entity.Rehearsal;
import com.moyeorock.global.config.JpaAuditingConfig;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({JpaAuditingConfig.class, TestcontainersConfig.class})
class RehearsalRepositoryTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 10, 1, 0, 0);

    @Autowired
    private RehearsalRepository rehearsalRepository;

    private Rehearsal save(Long teamId, String title, LocalDateTime startsAt) {
        return rehearsalRepository.saveAndFlush(
                Rehearsal.create(teamId, 10L, title, startsAt, startsAt.plusHours(2), "홍대", null));
    }

    @Test
    @DisplayName("findInPeriod는 시작 이상·끝 미만만 시작 시각 순으로 돌려주고 다른 팀은 뺀다")
    void findInPeriod_inRange_orderedByStart_excludesOtherTeam() {
        save(1L, "늦은", LocalDateTime.of(2026, 9, 20, 19, 0));
        save(1L, "이른", LocalDateTime.of(2026, 9, 3, 19, 0));
        save(1L, "시작 경계", START);
        save(1L, "끝 경계", END);
        save(1L, "범위 밖", LocalDateTime.of(2026, 8, 31, 23, 59));
        save(2L, "다른 팀", LocalDateTime.of(2026, 9, 10, 19, 0));

        List<Rehearsal> result = rehearsalRepository.findInPeriod(1L, START, END);

        assertThat(result).extracting(Rehearsal::getTitle).containsExactly("시작 경계", "이른", "늦은");
    }

    @Test
    @DisplayName("findInPeriod는 시작 시각이 같으면 id 순으로 돌려준다")
    void findInPeriod_sameStart_orderedById() {
        LocalDateTime same = LocalDateTime.of(2026, 9, 5, 19, 0);
        save(1L, "먼저", same);
        save(1L, "나중", same);

        List<Rehearsal> result = rehearsalRepository.findInPeriod(1L, START, END);

        assertThat(result).extracting(Rehearsal::getTitle).containsExactly("먼저", "나중");
    }

    @Test
    @DisplayName("findInPeriodByTeamIds는 목록의 팀만 시작 시각 순으로 섞어서 돌려준다")
    void findInPeriodByTeamIds_mergesTeamsOrderedByStart() {
        save(1L, "1팀 늦은", LocalDateTime.of(2026, 9, 20, 19, 0));
        save(2L, "2팀 이른", LocalDateTime.of(2026, 9, 5, 19, 0));
        save(3L, "목록에 없는 팀", LocalDateTime.of(2026, 9, 6, 19, 0));
        save(1L, "범위 밖", END);

        List<Rehearsal> result = rehearsalRepository.findInPeriodByTeamIds(List.of(1L, 2L), START, END);

        assertThat(result).extracting(Rehearsal::getTitle).containsExactly("2팀 이른", "1팀 늦은");
    }
}
