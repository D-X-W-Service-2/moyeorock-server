package com.moyeorock.domain.team.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.team.entity.Team;
import com.moyeorock.domain.team.entity.TeamGenre;
import com.moyeorock.domain.team.entity.TeamMember;
import com.moyeorock.domain.team.enums.MemberStatus;
import com.moyeorock.domain.team.enums.TeamRole;
import com.moyeorock.domain.team.enums.TeamStatus;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.config.JpaAuditingConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * 목록 필터(특히 EXISTS 서브쿼리)와 "ACTIVE 팀원·ACTIVE 팀" 규칙을 실제 MySQL에서 검증한다.
 * {@code @DataJpaTest}는 {@code @Configuration}을 자동 로드하지 않아 auditing을 명시적으로 켠다.
 */
@DataJpaTest
@Import({JpaAuditingConfig.class, TestcontainersConfig.class})
class TeamRepositoryTest {

    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 20);

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamGenreRepository teamGenreRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    private Team saveTeam(String name, Region region, Genre... genres) {
        Team team = teamRepository.saveAndFlush(Team.create(name, null, region));
        for (Genre genre : genres) {
            teamGenreRepository.saveAndFlush(TeamGenre.create(team.getId(), genre));
        }
        return team;
    }

    private Team saveDisbandedTeam(String name, Region region, Genre... genres) {
        Team team = saveTeam(name, region, genres);
        team.disband();
        return teamRepository.saveAndFlush(team);
    }

    private TeamMember saveMember(Team team, Long userId, MemberStatus status) {
        TeamMember member = TeamMember.create(team.getId(), userId, TeamRole.MEMBER, Instrument.BASS);
        if (status != MemberStatus.ACTIVE) {
            member.updateStatus(status);
        }
        return teamMemberRepository.saveAndFlush(member);
    }

    private static List<String> names(Page<Team> page) {
        return page.getContent().stream().map(Team::getName).toList();
    }

    @Test
    @DisplayName("search는 조건이 없으면 요청한 status의 팀을 최신순으로 돌려준다")
    void search_withoutFilters_returnsGivenStatusNewestFirst() {
        saveTeam("첫째", Region.SEOUL);
        saveTeam("둘째", Region.SEOUL);
        saveDisbandedTeam("해체됨", Region.SEOUL);

        Page<Team> active = teamRepository.search(null, null, TeamStatus.ACTIVE, FIRST_PAGE);
        Page<Team> disbanded = teamRepository.search(null, null, TeamStatus.DISBANDED, FIRST_PAGE);

        assertThat(names(active)).containsExactly("둘째", "첫째");
        assertThat(names(disbanded)).containsExactly("해체됨");
    }

    @Test
    @DisplayName("search는 region으로 거른다")
    void search_filtersByRegion() {
        saveTeam("서울팀", Region.SEOUL);
        saveTeam("경기팀", Region.GYEONGGI);

        Page<Team> result = teamRepository.search(Region.GYEONGGI, null, TeamStatus.ACTIVE, FIRST_PAGE);

        assertThat(names(result)).containsExactly("경기팀");
    }

    @Test
    @DisplayName("search는 장르로 거르고, 장르가 여러 개인 팀도 한 번만 나온다")
    void search_filtersByGenre_withoutDuplicateRows() {
        saveTeam("록과 인디", Region.SEOUL, Genre.ROCK, Genre.INDIE);
        saveTeam("재즈", Region.SEOUL, Genre.JAZZ);
        saveTeam("록만", Region.SEOUL, Genre.ROCK);

        Page<Team> result = teamRepository.search(null, Genre.ROCK, TeamStatus.ACTIVE, FIRST_PAGE);

        assertThat(names(result)).containsExactly("록만", "록과 인디");
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("search는 region과 genre를 함께 걸 수 있다")
    void search_filtersByRegionAndGenre() {
        saveTeam("서울 록", Region.SEOUL, Genre.ROCK);
        saveTeam("경기 록", Region.GYEONGGI, Genre.ROCK);
        saveTeam("서울 재즈", Region.SEOUL, Genre.JAZZ);

        Page<Team> result = teamRepository.search(Region.SEOUL, Genre.ROCK, TeamStatus.ACTIVE, FIRST_PAGE);

        assertThat(names(result)).containsExactly("서울 록");
    }

    @Test
    @DisplayName("findMyTeams는 내가 ACTIVE 팀원이고 팀도 ACTIVE인 팀만, 최근 합류한 순으로 돌려준다")
    void findMyTeams_returnsOnlyActiveMembershipOfActiveTeams() {
        Long me = 1L;
        Team joinedFirst = saveTeam("먼저 합류", Region.SEOUL);
        Team joinedLater = saveTeam("나중에 합류", Region.SEOUL);
        Team left = saveTeam("탈퇴한 팀", Region.SEOUL);
        Team removed = saveTeam("강퇴당한 팀", Region.SEOUL);
        Team disbanded = saveDisbandedTeam("해체된 팀", Region.SEOUL);
        Team othersTeam = saveTeam("남의 팀", Region.SEOUL);
        saveMember(joinedFirst, me, MemberStatus.ACTIVE);
        saveMember(joinedLater, me, MemberStatus.ACTIVE);
        saveMember(left, me, MemberStatus.LEFT);
        saveMember(removed, me, MemberStatus.REMOVED);
        saveMember(disbanded, me, MemberStatus.ACTIVE);
        saveMember(othersTeam, 2L, MemberStatus.ACTIVE);

        Page<Team> result = teamRepository.findMyTeams(me, FIRST_PAGE);

        assertThat(names(result)).containsExactly("나중에 합류", "먼저 합류");
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("findActiveByIdIn은 해체된 팀과 없는 id를 결과에서 뺀다")
    void findActiveByIdIn_excludesDisbandedAndUnknown() {
        Team active = saveTeam("활동 중", Region.SEOUL);
        Team disbanded = saveDisbandedTeam("해체됨", Region.SEOUL);

        List<Team> result = teamRepository.findActiveByIdIn(List.of(active.getId(), disbanded.getId(), 9999L));

        assertThat(result).extracting(Team::getName).containsExactly("활동 중");
    }
}
