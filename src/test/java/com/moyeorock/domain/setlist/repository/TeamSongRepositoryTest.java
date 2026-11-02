package com.moyeorock.domain.setlist.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.setlist.entity.TeamSong;
import com.moyeorock.global.config.JpaAuditingConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

/** 선택곡 전체 교체(벌크 삭제 → 다시 저장)가 UNIQUE(team_id, song_id)에 걸리지 않는지 실제 MySQL에서 확인한다. */
@DataJpaTest
@Import({JpaAuditingConfig.class, TestcontainersConfig.class})
class TeamSongRepositoryTest {

    private static final Long TEAM_ID = 5L;
    private static final Long OTHER_TEAM_ID = 6L;

    @Autowired
    private TeamSongRepository teamSongRepository;

    @Test
    @DisplayName("같은 트랜잭션에서 선택곡을 벌크 삭제하고 같은 곡을 다시 저장해도 유니크 제약에 걸리지 않는다")
    void deleteThenSaveSameSong_inSameTransaction() {
        teamSongRepository.saveAndFlush(TeamSong.create(TEAM_ID, null, 88L));

        teamSongRepository.deleteByTeamId(TEAM_ID);
        teamSongRepository.saveAllAndFlush(List.of(TeamSong.create(TEAM_ID, null, 88L), TeamSong.create(TEAM_ID, null, 91L)));

        assertThat(teamSongRepository.findAllByTeamIdOrderByIdAsc(TEAM_ID))
                .extracting(TeamSong::getSongId).containsExactly(88L, 91L);
    }

    @Test
    @DisplayName("팀 선택곡 조회·삭제는 그 팀 행만 다루고, 조회는 저장한 순서다")
    void findAndDelete_onlyTouchTheTeam() {
        teamSongRepository.save(TeamSong.create(TEAM_ID, 7L, 91L));
        teamSongRepository.save(TeamSong.create(TEAM_ID, 7L, 88L));
        teamSongRepository.saveAndFlush(TeamSong.create(OTHER_TEAM_ID, 7L, 88L));

        assertThat(teamSongRepository.findAllByTeamIdOrderByIdAsc(TEAM_ID))
                .extracting(TeamSong::getSongId).containsExactly(91L, 88L);

        teamSongRepository.deleteByTeamId(TEAM_ID);

        assertThat(teamSongRepository.findAllByTeamIdOrderByIdAsc(TEAM_ID)).isEmpty();
        assertThat(teamSongRepository.findAllByTeamIdOrderByIdAsc(OTHER_TEAM_ID)).hasSize(1);
    }
}
