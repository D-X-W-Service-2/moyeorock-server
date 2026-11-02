package com.moyeorock.domain.team.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moyeorock.domain.team.entity.Team;
import com.moyeorock.domain.team.entity.TeamMember;
import com.moyeorock.domain.team.enums.MemberStatus;
import com.moyeorock.domain.team.enums.TeamRole;
import com.moyeorock.domain.team.repository.TeamMemberRepository;
import com.moyeorock.domain.team.repository.TeamRepository;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TeamMemberServiceTest {

    private static final Long TEAM_ID = 5L;
    private static final Long LEADER_ID = 1L;
    private static final Long MEMBER_ID = 2L;

    @Mock
    TeamRepository teamRepository;
    @Mock
    TeamMemberRepository teamMemberRepository;
    @InjectMocks
    TeamMemberService teamMemberService;

    private Team team(Long id, String name, Long performanceId) {
        Team team = performanceId == null
                ? Team.create(name, null, Region.SEOUL)
                : Team.createForPerformance(performanceId, name, null, Region.SEOUL);
        ReflectionTestUtils.setField(team, "id", id);
        return team;
    }

    private Team disbandedTeam() {
        Team team = team(TEAM_ID, "무명밴드", null);
        team.disband();
        return team;
    }

    private TeamMember member(Long userId, TeamRole role, MemberStatus status) {
        TeamMember member = TeamMember.create(TEAM_ID, userId, role, Instrument.BASS);
        if (status != MemberStatus.ACTIVE) {
            member.updateStatus(status);
        }
        return member;
    }

    private void givenTeam(Team team) {
        given(teamRepository.findById(TEAM_ID)).willReturn(Optional.of(team));
    }

    private void givenMember(Long userId, TeamMember member) {
        given(teamMemberRepository.findByTeamIdAndUserId(TEAM_ID, userId)).willReturn(Optional.ofNullable(member));
    }

    private static void assertErrorCode(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }

    // ---------- ensureLeader ----------

    @Test
    @DisplayName("팀장 확인: 활동 중인 팀장이면 통과한다")
    void ensureLeader_passes_forActiveLeader() {
        givenTeam(team(TEAM_ID, "무명밴드", null));
        givenMember(LEADER_ID, member(LEADER_ID, TeamRole.LEADER, MemberStatus.ACTIVE));

        assertThatCode(() -> teamMemberService.ensureLeader(TEAM_ID, LEADER_ID)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("팀장 확인: 일반 팀원이거나 팀원이 아니면 NOT_TEAM_LEADER")
    void ensureLeader_rejects_memberAndStranger() {
        givenTeam(team(TEAM_ID, "무명밴드", null));
        givenMember(MEMBER_ID, member(MEMBER_ID, TeamRole.MEMBER, MemberStatus.ACTIVE));
        givenMember(99L, null);

        assertErrorCode(() -> teamMemberService.ensureLeader(TEAM_ID, MEMBER_ID), ErrorCode.NOT_TEAM_LEADER);
        assertErrorCode(() -> teamMemberService.ensureLeader(TEAM_ID, 99L), ErrorCode.NOT_TEAM_LEADER);
    }

    @Test
    @DisplayName("팀장 확인: 없는 팀이나 해체된 팀은 TEAM_NOT_FOUND")
    void ensureLeader_rejects_unknownAndDisbandedTeam() {
        given(teamRepository.findById(TEAM_ID)).willReturn(Optional.empty());
        assertErrorCode(() -> teamMemberService.ensureLeader(TEAM_ID, LEADER_ID), ErrorCode.TEAM_NOT_FOUND);

        givenTeam(disbandedTeam());
        assertErrorCode(() -> teamMemberService.ensureLeader(TEAM_ID, LEADER_ID), ErrorCode.TEAM_NOT_FOUND);
        verify(teamMemberRepository, never()).findByTeamIdAndUserId(TEAM_ID, LEADER_ID);
    }

    // ---------- ensureActiveMember ----------

    @Test
    @DisplayName("팀원 확인: 활동 중인 팀원이면 역할과 상관없이 통과한다")
    void ensureActiveMember_passes_forLeaderAndMember() {
        givenTeam(team(TEAM_ID, "무명밴드", null));
        givenMember(LEADER_ID, member(LEADER_ID, TeamRole.LEADER, MemberStatus.ACTIVE));
        givenMember(MEMBER_ID, member(MEMBER_ID, TeamRole.MEMBER, MemberStatus.ACTIVE));

        assertThatCode(() -> teamMemberService.ensureActiveMember(TEAM_ID, LEADER_ID)).doesNotThrowAnyException();
        assertThatCode(() -> teamMemberService.ensureActiveMember(TEAM_ID, MEMBER_ID)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("팀원 확인: 팀원이 아니거나 이미 나간 사람은 NO_PERMISSION")
    void ensureActiveMember_rejects_strangerAndFormerMember() {
        givenTeam(team(TEAM_ID, "무명밴드", null));
        givenMember(99L, null);
        givenMember(MEMBER_ID, member(MEMBER_ID, TeamRole.MEMBER, MemberStatus.LEFT));

        assertErrorCode(() -> teamMemberService.ensureActiveMember(TEAM_ID, 99L), ErrorCode.NO_PERMISSION);
        assertErrorCode(() -> teamMemberService.ensureActiveMember(TEAM_ID, MEMBER_ID), ErrorCode.NO_PERMISSION);
    }

    @Test
    @DisplayName("팀원 확인: 없는 팀이나 해체된 팀은 TEAM_NOT_FOUND")
    void ensureActiveMember_rejects_unknownAndDisbandedTeam() {
        given(teamRepository.findById(TEAM_ID)).willReturn(Optional.empty());
        assertErrorCode(() -> teamMemberService.ensureActiveMember(TEAM_ID, MEMBER_ID), ErrorCode.TEAM_NOT_FOUND);

        givenTeam(disbandedTeam());
        assertErrorCode(() -> teamMemberService.ensureActiveMember(TEAM_ID, MEMBER_ID), ErrorCode.TEAM_NOT_FOUND);
    }

    // ---------- getPerformanceId ----------

    @Test
    @DisplayName("공연 id: 공연 팀은 공연 id를, 독립 팀은 null을 돌려준다")
    void getPerformanceId_returnsIdOrNull() {
        givenTeam(team(TEAM_ID, "공연팀", 7L));
        assertThat(teamMemberService.getPerformanceId(TEAM_ID)).isEqualTo(7L);

        givenTeam(team(TEAM_ID, "독립 팀", null));
        assertThat(teamMemberService.getPerformanceId(TEAM_ID)).isNull();
    }

    @Test
    @DisplayName("공연 id: 없는 팀은 TEAM_NOT_FOUND, 해체된 팀은 그대로 돌려준다")
    void getPerformanceId_unknownIsNotFound_disbandedIsAllowed() {
        given(teamRepository.findById(TEAM_ID)).willReturn(Optional.empty());
        assertErrorCode(() -> teamMemberService.getPerformanceId(TEAM_ID), ErrorCode.TEAM_NOT_FOUND);

        Team disbanded = team(TEAM_ID, "끝난 공연팀", 7L);
        disbanded.disband();
        givenTeam(disbanded);
        assertThat(teamMemberService.getPerformanceId(TEAM_ID)).isEqualTo(7L);
    }

    // ---------- getNames ----------

    @Test
    @DisplayName("팀 이름: 해체된 팀의 이름도 돌려주고 없는 id는 빠진다")
    void getNames_includesDisbandedTeams() {
        Team active = team(5L, "활동 중", null);
        Team disbanded = team(6L, "해체된 팀", null);
        disbanded.disband();
        given(teamRepository.findAllById(List.of(5L, 6L, 99L))).willReturn(List.of(active, disbanded));

        Map<Long, String> names = teamMemberService.getNames(List.of(5L, 6L, 99L));

        assertThat(names).containsOnly(Map.entry(5L, "활동 중"), Map.entry(6L, "해체된 팀"));
    }

    @Test
    @DisplayName("팀 이름: id가 없거나 비었으면 조회하지 않고 빈 Map이다")
    void getNames_withoutIds_returnsEmpty() {
        assertThat(teamMemberService.getNames(null)).isEmpty();
        assertThat(teamMemberService.getNames(List.of())).isEmpty();
        verify(teamRepository, never()).findAllById(List.of());
    }
}
