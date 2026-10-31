package com.moyeorock.domain.team.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.team.entity.TeamGenre;
import com.moyeorock.global.common.enums.Genre;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(TestcontainersConfig.class)
class TeamGenreRepositoryTest {

    @Autowired
    private TeamGenreRepository teamGenreRepository;

    @Test
    @DisplayName("findAllByTeamIdIn은 여러 팀의 장르를 한 번에 가져온다")
    void findAllByTeamIdIn_returnsGenresOfGivenTeams() {
        teamGenreRepository.save(TeamGenre.create(1L, Genre.ROCK));
        teamGenreRepository.save(TeamGenre.create(1L, Genre.INDIE));
        teamGenreRepository.save(TeamGenre.create(2L, Genre.JAZZ));
        teamGenreRepository.saveAndFlush(TeamGenre.create(3L, Genre.POP));

        List<TeamGenre> result = teamGenreRepository.findAllByTeamIdIn(List.of(1L, 2L));

        assertThat(result).extracting(TeamGenre::getTeamId, TeamGenre::getGenre)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(1L, Genre.ROCK),
                        org.assertj.core.groups.Tuple.tuple(1L, Genre.INDIE),
                        org.assertj.core.groups.Tuple.tuple(2L, Genre.JAZZ));
    }

    @Test
    @DisplayName("deleteByTeamId는 그 팀의 장르만 지운다")
    void deleteByTeamId_deletesOnlyThatTeam() {
        teamGenreRepository.save(TeamGenre.create(1L, Genre.ROCK));
        teamGenreRepository.saveAndFlush(TeamGenre.create(2L, Genre.ROCK));

        teamGenreRepository.deleteByTeamId(1L);

        assertThat(teamGenreRepository.findAllByTeamIdIn(List.of(1L))).isEmpty();
        assertThat(teamGenreRepository.findAllByTeamIdIn(List.of(2L))).hasSize(1);
    }

    @Test
    @DisplayName("같은 트랜잭션에서 장르를 지우고 같은 장르를 다시 넣어도 유니크 위반이 나지 않는다")
    void deleteThenInsertSameGenre_inOneTransaction_doesNotViolateUnique() {
        teamGenreRepository.saveAndFlush(TeamGenre.create(1L, Genre.ROCK));

        teamGenreRepository.deleteByTeamId(1L);
        teamGenreRepository.saveAndFlush(TeamGenre.create(1L, Genre.ROCK));

        assertThat(teamGenreRepository.findAllByTeamIdIn(List.of(1L))).hasSize(1);
    }
}
