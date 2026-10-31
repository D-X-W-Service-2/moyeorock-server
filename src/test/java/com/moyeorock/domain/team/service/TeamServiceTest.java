package com.moyeorock.domain.team.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moyeorock.domain.team.dto.request.TeamCreateRequest;
import com.moyeorock.domain.team.dto.request.TeamMemberStatusUpdateRequest;
import com.moyeorock.domain.team.dto.request.TeamMemberUpdateRequest;
import com.moyeorock.domain.team.dto.request.TeamStatusUpdateRequest;
import com.moyeorock.domain.team.dto.request.TeamUpdateRequest;
import com.moyeorock.domain.team.dto.response.TeamDetailResponse;
import com.moyeorock.domain.team.dto.response.TeamMemberResponse;
import com.moyeorock.domain.team.dto.response.TeamMemberStatusResponse;
import com.moyeorock.domain.team.dto.response.TeamStatusResponse;
import com.moyeorock.domain.team.entity.Team;
import com.moyeorock.domain.team.entity.TeamGenre;
import com.moyeorock.domain.team.entity.TeamMember;
import com.moyeorock.domain.team.enums.MemberStatus;
import com.moyeorock.domain.team.enums.TeamRole;
import com.moyeorock.domain.team.enums.TeamStatus;
import com.moyeorock.domain.team.repository.TeamGenreRepository;
import com.moyeorock.domain.team.repository.TeamMemberRepository;
import com.moyeorock.domain.team.repository.TeamRepository;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import com.moyeorock.domain.user.service.UserService;
import com.moyeorock.global.common.enums.Genre;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    private static final Long TEAM_ID = 5L;
    private static final Long LEADER_ID = 1L;
    private static final Long MEMBER_ID = 2L;

    @Mock
    TeamRepository teamRepository;
    @Mock
    TeamGenreRepository teamGenreRepository;
    @Mock
    TeamMemberRepository teamMemberRepository;
    @Mock
    UserService userService;
    @InjectMocks
    TeamService teamService;

    private Team team() {
        Team team = Team.create("무명밴드", "소개", Region.SEOUL);
        ReflectionTestUtils.setField(team, "id", TEAM_ID);
        return team;
    }

    private Team disbandedTeam() {
        Team team = team();
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

    // ---------- create ----------

    @Test
    @DisplayName("팀 생성: 팀·중복 없는 장르·팀장 팀원을 저장하고 상세를 돌려준다")
    void create_savesTeamGenresAndLeader() {
        TeamCreateRequest request = new TeamCreateRequest("무명밴드", "소개", Region.SEOUL,
                List.of(Genre.ROCK, Genre.ROCK, Genre.INDIE), Instrument.BASS);
        given(teamRepository.save(any(Team.class))).willAnswer(inv -> {
            Team saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", TEAM_ID);
            return saved;
        });
        TeamMember leader = member(LEADER_ID, TeamRole.LEADER, MemberStatus.ACTIVE);
        // 서비스가 장르를 id 순으로 정렬하므로 저장된 것처럼 id를 채운다
        TeamGenre rock = TeamGenre.create(TEAM_ID, Genre.ROCK);
        TeamGenre indie = TeamGenre.create(TEAM_ID, Genre.INDIE);
        ReflectionTestUtils.setField(rock, "id", 1L);
        ReflectionTestUtils.setField(indie, "id", 2L);
        given(teamGenreRepository.findAllByTeamIdIn(List.of(TEAM_ID))).willReturn(List.of(indie, rock));
        given(teamMemberRepository.findActiveMembers(TEAM_ID)).willReturn(List.of(leader));
        UserSummaryResponse leaderUser = new UserSummaryResponse(LEADER_ID, "홍길동", null);
        given(userService.getSummaries(List.of(LEADER_ID))).willReturn(Map.of(LEADER_ID, leaderUser));

        TeamDetailResponse response = teamService.create(LEADER_ID, request);

        ArgumentCaptor<List<TeamGenre>> genres = ArgumentCaptor.forClass(List.class);
        verify(teamGenreRepository).saveAll(genres.capture());
        assertThat(genres.getValue()).extracting(TeamGenre::getGenre).containsExactly(Genre.ROCK, Genre.INDIE);
        ArgumentCaptor<TeamMember> saved = ArgumentCaptor.forClass(TeamMember.class);
        verify(teamMemberRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(LEADER_ID);
        assertThat(saved.getValue().getRole()).isEqualTo(TeamRole.LEADER);
        assertThat(saved.getValue().getInstrument()).isEqualTo(Instrument.BASS);

        assertThat(response.id()).isEqualTo(TEAM_ID);
        assertThat(response.status()).isEqualTo(TeamStatus.ACTIVE);
        assertThat(response.genres()).containsExactly(Genre.ROCK, Genre.INDIE);
        assertThat(response.leader()).isEqualTo(leaderUser);
        assertThat(response.memberCount()).isEqualTo(1);
        assertThat(response.myRole()).isEqualTo(TeamRole.LEADER);
    }

    // ---------- update ----------

    @Test
    @DisplayName("팀 정보 수정: 팀장이면 정보를 바꾸고 장르를 지운 뒤 새로 저장한다")
    void update_replacesInfoAndGenres() {
        Team team = team();
        givenTeam(team);
        givenMember(LEADER_ID, member(LEADER_ID, TeamRole.LEADER, MemberStatus.ACTIVE));

        teamService.update(LEADER_ID, TEAM_ID,
                new TeamUpdateRequest("유명밴드", "새 소개", Region.GYEONGGI, List.of(Genre.PUNK)));

        assertThat(team.getName()).isEqualTo("유명밴드");
        assertThat(team.getDescription()).isEqualTo("새 소개");
        assertThat(team.getRegion()).isEqualTo(Region.GYEONGGI);
        verify(teamGenreRepository).deleteByTeamId(TEAM_ID);
        verify(teamGenreRepository).saveAll(any());
    }

    @Test
    @DisplayName("팀 정보 수정: 팀원이거나 팀원이 아니면 NOT_TEAM_LEADER")
    void update_byNonLeader_isForbidden() {
        TeamUpdateRequest request = new TeamUpdateRequest("유명밴드", null, null, null);
        givenTeam(team());
        givenMember(MEMBER_ID, member(MEMBER_ID, TeamRole.MEMBER, MemberStatus.ACTIVE));
        assertErrorCode(() -> teamService.update(MEMBER_ID, TEAM_ID, request), ErrorCode.NOT_TEAM_LEADER);

        givenMember(99L, null);
        assertErrorCode(() -> teamService.update(99L, TEAM_ID, request), ErrorCode.NOT_TEAM_LEADER);
        verify(teamGenreRepository, never()).deleteByTeamId(any());
    }

    @Test
    @DisplayName("팀 정보 수정: 없는 팀은 TEAM_NOT_FOUND")
    void update_unknownTeam_isNotFound() {
        given(teamRepository.findById(TEAM_ID)).willReturn(Optional.empty());

        assertErrorCode(() -> teamService.update(LEADER_ID, TEAM_ID,
                new TeamUpdateRequest("유명밴드", null, null, null)), ErrorCode.TEAM_NOT_FOUND);
    }

    @Test
    @DisplayName("팀 정보 수정: 해체된 팀은 INVALID_STATE")
    void update_disbandedTeam_isInvalidState() {
        givenTeam(disbandedTeam());
        givenMember(LEADER_ID, member(LEADER_ID, TeamRole.LEADER, MemberStatus.ACTIVE));

        assertErrorCode(() -> teamService.update(LEADER_ID, TEAM_ID,
                new TeamUpdateRequest("유명밴드", null, null, null)), ErrorCode.INVALID_STATE);
    }

    // ---------- disband ----------

    @Test
    @DisplayName("팀 해체: 팀장이 DISBANDED를 보내면 팀 상태가 DISBANDED가 된다")
    void disband_changesStatus() {
        Team team = team();
        givenTeam(team);
        givenMember(LEADER_ID, member(LEADER_ID, TeamRole.LEADER, MemberStatus.ACTIVE));

        TeamStatusResponse response = teamService.disband(LEADER_ID, TEAM_ID,
                new TeamStatusUpdateRequest(TeamStatus.DISBANDED));

        assertThat(team.getStatus()).isEqualTo(TeamStatus.DISBANDED);
        assertThat(response).isEqualTo(new TeamStatusResponse(TEAM_ID, TeamStatus.DISBANDED));
    }

    @Test
    @DisplayName("팀 해체: DISBANDED가 아닌 값은 VALIDATION_FAILED")
    void disband_withOtherStatus_isValidationFailed() {
        assertErrorCode(() -> teamService.disband(LEADER_ID, TEAM_ID,
                new TeamStatusUpdateRequest(TeamStatus.ACTIVE)), ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("팀 해체: 팀장이 아니면 NOT_TEAM_LEADER")
    void disband_byNonLeader_isForbidden() {
        givenTeam(team());
        givenMember(MEMBER_ID, member(MEMBER_ID, TeamRole.MEMBER, MemberStatus.ACTIVE));

        assertErrorCode(() -> teamService.disband(MEMBER_ID, TEAM_ID,
                new TeamStatusUpdateRequest(TeamStatus.DISBANDED)), ErrorCode.NOT_TEAM_LEADER);
    }

    @Test
    @DisplayName("팀 해체: 이미 해체된 팀을 다시 해체하면 INVALID_STATE")
    void disband_alreadyDisbanded_isInvalidState() {
        givenTeam(disbandedTeam());
        givenMember(LEADER_ID, member(LEADER_ID, TeamRole.LEADER, MemberStatus.ACTIVE));

        assertErrorCode(() -> teamService.disband(LEADER_ID, TEAM_ID,
                new TeamStatusUpdateRequest(TeamStatus.DISBANDED)), ErrorCode.INVALID_STATE);
    }

    // ---------- changeMember ----------

    @Test
    @DisplayName("팀원 변경: role = LEADER면 팀장이 위임되어 기존 팀장은 MEMBER가 된다")
    void changeMember_delegation_swapsRoles() {
        givenTeam(team());
        TeamMember leader = member(LEADER_ID, TeamRole.LEADER, MemberStatus.ACTIVE);
        TeamMember target = member(MEMBER_ID, TeamRole.MEMBER, MemberStatus.ACTIVE);
        givenMember(LEADER_ID, leader);
        givenMember(MEMBER_ID, target);
        given(userService.getSummaries(List.of(MEMBER_ID)))
                .willReturn(Map.of(MEMBER_ID, new UserSummaryResponse(MEMBER_ID, "김철수", null)));

        TeamMemberResponse response = teamService.changeMember(LEADER_ID, TEAM_ID, MEMBER_ID,
                new TeamMemberUpdateRequest(TeamRole.LEADER, null));

        assertThat(leader.getRole()).isEqualTo(TeamRole.MEMBER);
        assertThat(target.getRole()).isEqualTo(TeamRole.LEADER);
        assertThat(response.role()).isEqualTo(TeamRole.LEADER);
        assertThat(response.user().nickname()).isEqualTo("김철수");
    }

    @Test
    @DisplayName("팀원 변경: instrument만 보내면 세션만 바뀌고 역할은 그대로다")
    void changeMember_instrumentOnly_keepsRole() {
        givenTeam(team());
        TeamMember target = member(MEMBER_ID, TeamRole.MEMBER, MemberStatus.ACTIVE);
        givenMember(LEADER_ID, member(LEADER_ID, TeamRole.LEADER, MemberStatus.ACTIVE));
        givenMember(MEMBER_ID, target);
        given(userService.getSummaries(List.of(MEMBER_ID))).willReturn(Map.of());

        teamService.changeMember(LEADER_ID, TEAM_ID, MEMBER_ID, new TeamMemberUpdateRequest(null, Instrument.DRUM));

        assertThat(target.getInstrument()).isEqualTo(Instrument.DRUM);
        assertThat(target.getRole()).isEqualTo(TeamRole.MEMBER);
    }

    @Test
    @DisplayName("팀원 변경: 팀장이 아니면 NOT_TEAM_LEADER")
    void changeMember_byNonLeader_isForbidden() {
        givenTeam(team());
        givenMember(MEMBER_ID, member(MEMBER_ID, TeamRole.MEMBER, MemberStatus.ACTIVE));

        assertErrorCode(() -> teamService.changeMember(MEMBER_ID, TEAM_ID, 3L,
                new TeamMemberUpdateRequest(TeamRole.LEADER, null)), ErrorCode.NOT_TEAM_LEADER);
    }

    @Test
    @DisplayName("팀원 변경: 대상이 팀원이 아니거나 이미 나간 사람이면 INVALID_STATE")
    void changeMember_targetNotActive_isInvalidState() {
        givenTeam(team());
        givenMember(LEADER_ID, member(LEADER_ID, TeamRole.LEADER, MemberStatus.ACTIVE));
        givenMember(MEMBER_ID, member(MEMBER_ID, TeamRole.MEMBER, MemberStatus.LEFT));

        assertErrorCode(() -> teamService.changeMember(LEADER_ID, TEAM_ID, MEMBER_ID,
                new TeamMemberUpdateRequest(TeamRole.LEADER, null)), ErrorCode.INVALID_STATE);
    }

    @Test
    @DisplayName("팀원 변경: 팀장 자신을 MEMBER로 내리면 팀장이 없어지므로 INVALID_STATE")
    void changeMember_demotingTheLeader_isInvalidState() {
        givenTeam(team());
        TeamMember leader = member(LEADER_ID, TeamRole.LEADER, MemberStatus.ACTIVE);
        givenMember(LEADER_ID, leader);

        assertErrorCode(() -> teamService.changeMember(LEADER_ID, TEAM_ID, LEADER_ID,
                new TeamMemberUpdateRequest(TeamRole.MEMBER, null)), ErrorCode.INVALID_STATE);
        assertThat(leader.getRole()).isEqualTo(TeamRole.LEADER);
    }

    // ---------- changeMemberStatus ----------

    @Test
    @DisplayName("탈퇴: 본인이 LEFT를 보내면 상태가 LEFT가 된다")
    void leave_bySelf_changesStatusToLeft() {
        givenTeam(team());
        TeamMember me = member(MEMBER_ID, TeamRole.MEMBER, MemberStatus.ACTIVE);
        givenMember(MEMBER_ID, me);

        TeamMemberStatusResponse response = teamService.changeMemberStatus(MEMBER_ID, TEAM_ID, MEMBER_ID,
                new TeamMemberStatusUpdateRequest(MemberStatus.LEFT));

        assertThat(me.getStatus()).isEqualTo(MemberStatus.LEFT);
        assertThat(response).isEqualTo(new TeamMemberStatusResponse(TEAM_ID, MEMBER_ID, MemberStatus.LEFT));
    }

    @Test
    @DisplayName("탈퇴: 남을 대신 탈퇴시키려 하면 NO_PERMISSION")
    void leave_forSomeoneElse_isNoPermission() {
        givenTeam(team());

        assertErrorCode(() -> teamService.changeMemberStatus(LEADER_ID, TEAM_ID, MEMBER_ID,
                new TeamMemberStatusUpdateRequest(MemberStatus.LEFT)), ErrorCode.NO_PERMISSION);
    }

    @Test
    @DisplayName("탈퇴: 팀장은 위임 전에는 LEADER_MUST_DELEGATE_FIRST")
    void leave_asLeader_mustDelegateFirst() {
        givenTeam(team());
        TeamMember leader = member(LEADER_ID, TeamRole.LEADER, MemberStatus.ACTIVE);
        givenMember(LEADER_ID, leader);

        assertErrorCode(() -> teamService.changeMemberStatus(LEADER_ID, TEAM_ID, LEADER_ID,
                new TeamMemberStatusUpdateRequest(MemberStatus.LEFT)), ErrorCode.LEADER_MUST_DELEGATE_FIRST);
        assertThat(leader.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("강퇴: 팀장이 REMOVED를 보내면 대상 상태가 REMOVED가 된다")
    void remove_byLeader_changesStatusToRemoved() {
        givenTeam(team());
        TeamMember target = member(MEMBER_ID, TeamRole.MEMBER, MemberStatus.ACTIVE);
        givenMember(LEADER_ID, member(LEADER_ID, TeamRole.LEADER, MemberStatus.ACTIVE));
        givenMember(MEMBER_ID, target);

        TeamMemberStatusResponse response = teamService.changeMemberStatus(LEADER_ID, TEAM_ID, MEMBER_ID,
                new TeamMemberStatusUpdateRequest(MemberStatus.REMOVED));

        assertThat(target.getStatus()).isEqualTo(MemberStatus.REMOVED);
        assertThat(response.status()).isEqualTo(MemberStatus.REMOVED);
    }

    @Test
    @DisplayName("강퇴: 팀장이 아니면 NOT_TEAM_LEADER")
    void remove_byNonLeader_isForbidden() {
        givenTeam(team());
        givenMember(MEMBER_ID, member(MEMBER_ID, TeamRole.MEMBER, MemberStatus.ACTIVE));

        assertErrorCode(() -> teamService.changeMemberStatus(MEMBER_ID, TEAM_ID, 3L,
                new TeamMemberStatusUpdateRequest(MemberStatus.REMOVED)), ErrorCode.NOT_TEAM_LEADER);
    }

    @Test
    @DisplayName("강퇴: 대상이 이미 나간 사람이면 INVALID_STATE")
    void remove_targetNotActive_isInvalidState() {
        givenTeam(team());
        givenMember(LEADER_ID, member(LEADER_ID, TeamRole.LEADER, MemberStatus.ACTIVE));
        givenMember(MEMBER_ID, member(MEMBER_ID, TeamRole.MEMBER, MemberStatus.LEFT));

        assertErrorCode(() -> teamService.changeMemberStatus(LEADER_ID, TEAM_ID, MEMBER_ID,
                new TeamMemberStatusUpdateRequest(MemberStatus.REMOVED)), ErrorCode.INVALID_STATE);
    }

    @Test
    @DisplayName("강퇴: 팀장이 자기 자신을 강퇴하면 INVALID_STATE")
    void remove_theLeaderHimself_isInvalidState() {
        givenTeam(team());
        givenMember(LEADER_ID, member(LEADER_ID, TeamRole.LEADER, MemberStatus.ACTIVE));

        assertErrorCode(() -> teamService.changeMemberStatus(LEADER_ID, TEAM_ID, LEADER_ID,
                new TeamMemberStatusUpdateRequest(MemberStatus.REMOVED)), ErrorCode.INVALID_STATE);
    }

    @Test
    @DisplayName("상태 변경: LEFT·REMOVED가 아닌 값은 VALIDATION_FAILED")
    void changeMemberStatus_withOtherStatus_isValidationFailed() {
        assertErrorCode(() -> teamService.changeMemberStatus(LEADER_ID, TEAM_ID, MEMBER_ID,
                new TeamMemberStatusUpdateRequest(MemberStatus.ACTIVE)), ErrorCode.VALIDATION_FAILED);
    }
}
