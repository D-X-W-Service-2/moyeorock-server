package com.moyeorock.domain.team.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.team.entity.TeamMember;
import com.moyeorock.domain.team.enums.MemberStatus;
import com.moyeorock.domain.team.enums.TeamRole;
import com.moyeorock.domain.team.repository.TeamMemberRepository.TeamMemberCount;
import com.moyeorock.global.common.enums.Instrument;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@Import(TestcontainersConfig.class)
class TeamMemberRepositoryTest {

    private static final Long TEAM_ID = 1L;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    private TeamMember save(Long teamId, Long userId, TeamRole role, MemberStatus status) {
        TeamMember member = TeamMember.create(teamId, userId, role, Instrument.BASS);
        if (status != MemberStatus.ACTIVE) {
            member.updateStatus(status);
        }
        return teamMemberRepository.saveAndFlush(member);
    }

    @Test
    @DisplayName("findActiveMembers는 탈퇴·강퇴한 팀원을 빼고 합류 순으로 돌려준다")
    void findActiveMembers_excludesLeftAndRemoved_orderedByJoin() {
        save(TEAM_ID, 10L, TeamRole.LEADER, MemberStatus.ACTIVE);
        save(TEAM_ID, 11L, TeamRole.MEMBER, MemberStatus.LEFT);
        save(TEAM_ID, 12L, TeamRole.MEMBER, MemberStatus.ACTIVE);
        save(TEAM_ID, 13L, TeamRole.MEMBER, MemberStatus.REMOVED);
        save(2L, 14L, TeamRole.MEMBER, MemberStatus.ACTIVE);

        List<TeamMember> result = teamMemberRepository.findActiveMembers(TEAM_ID);

        assertThat(result).extracting(TeamMember::getUserId).containsExactly(10L, 12L);
    }

    @Test
    @DisplayName("findByTeamIdAndUserId는 상태와 무관하게 그 사람의 행을 찾는다")
    void findByTeamIdAndUserId_ignoresStatus() {
        save(TEAM_ID, 10L, TeamRole.MEMBER, MemberStatus.LEFT);

        assertThat(teamMemberRepository.findByTeamIdAndUserId(TEAM_ID, 10L))
                .get().extracting(TeamMember::getStatus).isEqualTo(MemberStatus.LEFT);
        assertThat(teamMemberRepository.findByTeamIdAndUserId(TEAM_ID, 99L)).isEmpty();
    }

    @Test
    @DisplayName("findActiveLeader는 ACTIVE인 팀장만 찾는다")
    void findActiveLeader_returnsOnlyActiveLeader() {
        save(TEAM_ID, 10L, TeamRole.LEADER, MemberStatus.ACTIVE);
        save(TEAM_ID, 11L, TeamRole.MEMBER, MemberStatus.ACTIVE);
        save(2L, 20L, TeamRole.LEADER, MemberStatus.LEFT);

        assertThat(teamMemberRepository.findActiveLeader(TEAM_ID))
                .get().extracting(TeamMember::getUserId).isEqualTo(10L);
        assertThat(teamMemberRepository.findActiveLeader(2L)).isEmpty();
    }

    @Test
    @DisplayName("countActiveByTeamIds는 ACTIVE 팀원만 팀별로 세고, 팀원이 없는 팀은 결과에 나오지 않는다")
    void countActiveByTeamIds_countsActiveOnlyPerTeam() {
        save(1L, 10L, TeamRole.LEADER, MemberStatus.ACTIVE);
        save(1L, 11L, TeamRole.MEMBER, MemberStatus.ACTIVE);
        save(1L, 12L, TeamRole.MEMBER, MemberStatus.LEFT);
        save(2L, 20L, TeamRole.LEADER, MemberStatus.ACTIVE);

        List<TeamMemberCount> result = teamMemberRepository.countActiveByTeamIds(List.of(1L, 2L, 3L));

        assertThat(result).extracting(TeamMemberCount::getTeamId, TeamMemberCount::getCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(1L, 2L),
                        org.assertj.core.groups.Tuple.tuple(2L, 1L));
    }

    @Test
    @DisplayName("같은 팀에 같은 사람을 두 번 저장하면 유니크 위반이다 — 재가입은 새 행이 아니라 기존 행 복구")
    void save_sameTeamAndUserTwice_violatesUnique() {
        save(TEAM_ID, 10L, TeamRole.MEMBER, MemberStatus.LEFT);

        assertThatThrownBy(() -> save(TEAM_ID, 10L, TeamRole.MEMBER, MemberStatus.ACTIVE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
