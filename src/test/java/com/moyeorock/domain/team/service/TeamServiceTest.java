package com.moyeorock.domain.team.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moyeorock.domain.setlist.dto.response.TeamSongResponse;
import com.moyeorock.domain.setlist.service.SetlistService;
import com.moyeorock.domain.song.dto.response.SongSummaryResponse;
import com.moyeorock.domain.team.client.AiMemberRecommendationClient;
import com.moyeorock.domain.team.client.AiRecommendRequest;
import com.moyeorock.domain.team.client.AiRecommendResponse;
import com.moyeorock.domain.team.dto.request.TeamCreateRequest;
import com.moyeorock.domain.team.dto.request.TeamMemberStatusUpdateRequest;
import com.moyeorock.domain.team.dto.request.TeamMemberUpdateRequest;
import com.moyeorock.domain.team.dto.request.TeamStatusUpdateRequest;
import com.moyeorock.domain.team.dto.request.TeamUpdateRequest;
import com.moyeorock.domain.team.dto.response.PerformanceTeamResponse;
import com.moyeorock.domain.team.dto.response.TeamDetailResponse;
import com.moyeorock.domain.team.dto.response.TeamMemberRecommendationResponse;
import com.moyeorock.domain.team.dto.response.TeamMemberResponse;
import com.moyeorock.domain.team.dto.response.TeamMemberStatusResponse;
import com.moyeorock.domain.team.dto.response.TeamMembersResponse;
import com.moyeorock.domain.team.dto.response.TeamMembershipResponse;
import com.moyeorock.domain.team.dto.response.TeamStatusResponse;
import com.moyeorock.domain.team.dto.response.TeamSummaryResponse;
import com.moyeorock.domain.team.entity.Team;
import com.moyeorock.domain.team.entity.TeamGenre;
import com.moyeorock.domain.team.entity.TeamMember;
import com.moyeorock.domain.team.enums.MemberStatus;
import com.moyeorock.domain.team.enums.TeamRole;
import com.moyeorock.domain.team.enums.TeamStatus;
import com.moyeorock.domain.team.repository.TeamGenreRepository;
import com.moyeorock.domain.team.repository.TeamMemberRepository;
import com.moyeorock.domain.team.repository.TeamMemberRepository.TeamMemberCount;
import com.moyeorock.domain.team.repository.TeamRepository;
import com.moyeorock.domain.user.dto.response.RecommendationCandidateResponse;
import com.moyeorock.domain.user.dto.response.UserInstrumentResponse;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import com.moyeorock.domain.performance.dto.response.PerformanceBriefResponse;
import com.moyeorock.domain.performance.service.PerformanceMemberService;
import com.moyeorock.domain.user.service.UserService;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    @Mock
    PerformanceMemberService performanceMemberService;
    @Mock
    SetlistService setlistService;
    @Mock
    AiMemberRecommendationClient aiMemberRecommendationClient;
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

    // 쓰기 메서드는 팀 행을 잠그고(findByIdForUpdate), 조회 메서드는 일반 조회(findById)를 쓴다.
    // 어느 쪽을 쓰는지는 각 테스트가 정할 일이 아니므로 둘 다 같은 팀을 돌려주게 해 둔다.
    private void givenTeam(Team team) {
        lenient().when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team));
        lenient().when(teamRepository.findByIdForUpdate(TEAM_ID)).thenReturn(Optional.of(team));
    }

    private void givenNoTeam() {
        lenient().when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.empty());
        lenient().when(teamRepository.findByIdForUpdate(TEAM_ID)).thenReturn(Optional.empty());
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
        givenNoTeam();

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
    @DisplayName("팀 해체: 해체되면 그 팀의 선택곡·셋리스트를 지운다")
    void disband_deletesSetlistAndTeamSongs() {
        givenTeam(team());
        givenMember(LEADER_ID, member(LEADER_ID, TeamRole.LEADER, MemberStatus.ACTIVE));

        teamService.disband(LEADER_ID, TEAM_ID, new TeamStatusUpdateRequest(TeamStatus.DISBANDED));

        verify(setlistService).deleteByTeam(TEAM_ID);
    }

    @Test
    @DisplayName("팀 해체: DISBANDED가 아닌 값은 VALIDATION_FAILED")
    void disband_withOtherStatus_isValidationFailed() {
        assertErrorCode(() -> teamService.disband(LEADER_ID, TEAM_ID,
                new TeamStatusUpdateRequest(TeamStatus.ACTIVE)), ErrorCode.VALIDATION_FAILED);
        verify(setlistService, never()).deleteByTeam(any());
    }

    @Test
    @DisplayName("팀 해체: 팀장이 아니면 NOT_TEAM_LEADER이고 선택곡·셋리스트는 지우지 않는다")
    void disband_byNonLeader_isForbidden() {
        givenTeam(team());
        givenMember(MEMBER_ID, member(MEMBER_ID, TeamRole.MEMBER, MemberStatus.ACTIVE));

        assertErrorCode(() -> teamService.disband(MEMBER_ID, TEAM_ID,
                new TeamStatusUpdateRequest(TeamStatus.DISBANDED)), ErrorCode.NOT_TEAM_LEADER);
        verify(setlistService, never()).deleteByTeam(any());
    }

    @Test
    @DisplayName("팀 해체: 이미 해체된 팀을 다시 해체하면 INVALID_STATE이고 선택곡·셋리스트는 지우지 않는다")
    void disband_alreadyDisbanded_isInvalidState() {
        givenTeam(disbandedTeam());
        givenMember(LEADER_ID, member(LEADER_ID, TeamRole.LEADER, MemberStatus.ACTIVE));

        assertErrorCode(() -> teamService.disband(LEADER_ID, TEAM_ID,
                new TeamStatusUpdateRequest(TeamStatus.DISBANDED)), ErrorCode.INVALID_STATE);
        verify(setlistService, never()).deleteByTeam(any());
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
    @DisplayName("팀원 변경: 대상이 팀원이 아니거나 이미 나간 사람이면 TEAM_MEMBER_NOT_FOUND")
    void changeMember_targetNotActive_isMemberNotFound() {
        givenTeam(team());
        givenMember(LEADER_ID, member(LEADER_ID, TeamRole.LEADER, MemberStatus.ACTIVE));
        givenMember(MEMBER_ID, member(MEMBER_ID, TeamRole.MEMBER, MemberStatus.LEFT));
        givenMember(99L, null);

        assertErrorCode(() -> teamService.changeMember(LEADER_ID, TEAM_ID, MEMBER_ID,
                new TeamMemberUpdateRequest(TeamRole.LEADER, null)), ErrorCode.TEAM_MEMBER_NOT_FOUND);
        assertErrorCode(() -> teamService.changeMember(LEADER_ID, TEAM_ID, 99L,
                new TeamMemberUpdateRequest(TeamRole.LEADER, null)), ErrorCode.TEAM_MEMBER_NOT_FOUND);
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
    @DisplayName("강퇴: 대상이 이미 나간 사람이거나 팀원이 아니면 TEAM_MEMBER_NOT_FOUND")
    void remove_targetNotActive_isMemberNotFound() {
        givenTeam(team());
        givenMember(LEADER_ID, member(LEADER_ID, TeamRole.LEADER, MemberStatus.ACTIVE));
        givenMember(MEMBER_ID, member(MEMBER_ID, TeamRole.MEMBER, MemberStatus.LEFT));
        givenMember(99L, null);

        assertErrorCode(() -> teamService.changeMemberStatus(LEADER_ID, TEAM_ID, MEMBER_ID,
                new TeamMemberStatusUpdateRequest(MemberStatus.REMOVED)), ErrorCode.TEAM_MEMBER_NOT_FOUND);
        assertErrorCode(() -> teamService.changeMemberStatus(LEADER_ID, TEAM_ID, 99L,
                new TeamMemberStatusUpdateRequest(MemberStatus.REMOVED)), ErrorCode.TEAM_MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("탈퇴: 이미 나간 사람이 다시 탈퇴를 요청하면 TEAM_MEMBER_NOT_FOUND")
    void leave_whenAlreadyLeft_isMemberNotFound() {
        givenTeam(team());
        givenMember(MEMBER_ID, member(MEMBER_ID, TeamRole.MEMBER, MemberStatus.LEFT));

        assertErrorCode(() -> teamService.changeMemberStatus(MEMBER_ID, TEAM_ID, MEMBER_ID,
                new TeamMemberStatusUpdateRequest(MemberStatus.LEFT)), ErrorCode.TEAM_MEMBER_NOT_FOUND);
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

    // ---------- 조회 ----------

    private TeamGenre genre(Long id, Long teamId, Genre genre) {
        TeamGenre teamGenre = TeamGenre.create(teamId, genre);
        ReflectionTestUtils.setField(teamGenre, "id", id);
        return teamGenre;
    }

    private static TeamMemberCount count(Long teamId, long count) {
        return new TeamMemberCount() {
            @Override
            public Long getTeamId() {
                return teamId;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }

    @Test
    @DisplayName("팀 목록: ACTIVE 팀만 조회하고 장르·인원 수를 채우며, 팀원이 없는 팀의 인원 수는 0이다")
    void search_buildsSummaries_withGenresAndCounts() {
        Team crowded = team();
        Team empty = Team.create("빈 팀", null, Region.GYEONGGI);
        ReflectionTestUtils.setField(empty, "id", 6L);
        Pageable pageable = PageRequest.of(0, 20);
        given(teamRepository.search(Region.SEOUL, Genre.ROCK, TeamStatus.ACTIVE, pageable))
                .willReturn(new PageImpl<>(List.of(crowded, empty), pageable, 2));
        given(teamGenreRepository.findAllByTeamIdIn(List.of(TEAM_ID, 6L)))
                .willReturn(List.of(genre(2L, TEAM_ID, Genre.INDIE), genre(1L, TEAM_ID, Genre.ROCK)));
        given(teamMemberRepository.countActiveByTeamIds(List.of(TEAM_ID, 6L)))
                .willReturn(List.of(count(TEAM_ID, 4)));

        PageResponse<TeamSummaryResponse> response = teamService.search(Region.SEOUL, Genre.ROCK, pageable);

        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.content()).containsExactly(
                new TeamSummaryResponse(TEAM_ID, "무명밴드", Region.SEOUL, List.of(Genre.ROCK, Genre.INDIE), TeamStatus.ACTIVE, 4),
                new TeamSummaryResponse(6L, "빈 팀", Region.GYEONGGI, List.of(), TeamStatus.ACTIVE, 0));
    }

    @Test
    @DisplayName("팀 목록: 클라이언트가 보낸 sort는 버리고 쿼리의 고정 정렬을 쓴다")
    void search_dropsClientSort() {
        given(teamRepository.search(eq(null), eq(null), eq(TeamStatus.ACTIVE), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        teamService.search(null, null, PageRequest.of(2, 10, Sort.by("name")));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(teamRepository).search(eq(null), eq(null), eq(TeamStatus.ACTIVE), captor.capture());
        assertThat(captor.getValue().getSort().isUnsorted()).isTrue();
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("팀 목록: 결과가 비면 장르·인원 수 쿼리를 날리지 않는다")
    void search_emptyPage_skipsExtraQueries() {
        Pageable pageable = PageRequest.of(0, 20);
        given(teamRepository.search(null, null, TeamStatus.ACTIVE, pageable)).willReturn(new PageImpl<>(List.of()));

        PageResponse<TeamSummaryResponse> response = teamService.search(null, null, pageable);

        assertThat(response.content()).isEmpty();
        verify(teamGenreRepository, never()).findAllByTeamIdIn(any());
        verify(teamMemberRepository, never()).countActiveByTeamIds(any());
    }

    @Test
    @DisplayName("내 팀 목록: 내 팀을 요약으로 돌려준다")
    void getMyTeams_returnsSummaries() {
        Pageable pageable = PageRequest.of(0, 20);
        given(teamRepository.findMyTeams(MEMBER_ID, pageable))
                .willReturn(new PageImpl<>(List.of(team()), pageable, 1));
        given(teamGenreRepository.findAllByTeamIdIn(List.of(TEAM_ID))).willReturn(List.of());
        given(teamMemberRepository.countActiveByTeamIds(List.of(TEAM_ID))).willReturn(List.of(count(TEAM_ID, 2)));

        PageResponse<TeamSummaryResponse> response = teamService.getMyTeams(MEMBER_ID, pageable);

        assertThat(response.content()).containsExactly(
                new TeamSummaryResponse(TEAM_ID, "무명밴드", Region.SEOUL, List.of(), TeamStatus.ACTIVE, 2));
    }

    @Test
    @DisplayName("팀 상세: 팀원이 보면 myRole이 채워지고, 팀원이 아닌 사람이 보면 null이다")
    void getDetail_myRole_dependsOnViewer() {
        givenTeam(team());
        given(teamGenreRepository.findAllByTeamIdIn(List.of(TEAM_ID))).willReturn(List.of());
        TeamMember leader = member(LEADER_ID, TeamRole.LEADER, MemberStatus.ACTIVE);
        TeamMember member = member(MEMBER_ID, TeamRole.MEMBER, MemberStatus.ACTIVE);
        given(teamMemberRepository.findActiveMembers(TEAM_ID)).willReturn(List.of(leader, member));
        given(userService.getSummaries(List.of(LEADER_ID, MEMBER_ID))).willReturn(Map.of(
                LEADER_ID, new UserSummaryResponse(LEADER_ID, "홍길동", null),
                MEMBER_ID, new UserSummaryResponse(MEMBER_ID, "김철수", null)));

        assertThat(teamService.getDetail(MEMBER_ID, TEAM_ID).myRole()).isEqualTo(TeamRole.MEMBER);
        TeamDetailResponse asStranger = teamService.getDetail(99L, TEAM_ID);

        assertThat(asStranger.myRole()).isNull();
        assertThat(asStranger.memberCount()).isEqualTo(2);
        assertThat(asStranger.leader().nickname()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("팀 상세: 선택곡이 songs에 담기고, 고른 곡이 없으면 빈 목록이다")
    void getDetail_includesTeamSongs() {
        givenTeam(team());
        given(teamMemberRepository.findActiveMembers(TEAM_ID)).willReturn(List.of());
        given(userService.getSummaries(List.of())).willReturn(Map.of());
        TeamSongResponse teamSong = new TeamSongResponse(21L, new SongSummaryResponse(88L, "Bohemian Rhapsody", "Queen"));
        given(setlistService.findTeamSongs(TEAM_ID)).willReturn(List.of(teamSong)).willReturn(List.of());

        assertThat(teamService.getDetail(LEADER_ID, TEAM_ID).songs()).containsExactly(teamSong);
        assertThat(teamService.getDetail(LEADER_ID, TEAM_ID).songs()).isEmpty();
    }

    @Test
    @DisplayName("팀 상세: 공연 팀이면 소속 공연 요약이 담기고 독립 팀이면 null이다")
    void getDetail_performanceIsBriefForPerformanceTeam_nullForIndependentTeam() {
        PerformanceBriefResponse brief = new PerformanceBriefResponse(7L, "정기공연", "홍대 밴드부",
                java.time.LocalDateTime.of(2026, 11, 22, 18, 0));
        given(performanceMemberService.getBrief(7L)).willReturn(brief);
        given(teamMemberRepository.findActiveMembers(TEAM_ID)).willReturn(List.of());
        given(userService.getSummaries(List.of())).willReturn(Map.of());

        givenTeam(performanceTeam(TEAM_ID, 7L));
        assertThat(teamService.getDetail(LEADER_ID, TEAM_ID).performance()).isEqualTo(brief);

        givenTeam(team());
        assertThat(teamService.getDetail(LEADER_ID, TEAM_ID).performance()).isNull();
        verify(performanceMemberService).getBrief(7L);
    }

    @Test
    @DisplayName("팀 상세·팀원 목록: 해체된 팀도 읽을 수 있고 상세의 status가 DISBANDED다")
    void reads_disbandedTeam_isReadable() {
        givenTeam(disbandedTeam());
        TeamMember leader = member(LEADER_ID, TeamRole.LEADER, MemberStatus.ACTIVE);
        given(teamMemberRepository.findActiveMembers(TEAM_ID)).willReturn(List.of(leader));
        given(userService.getSummaries(List.of(LEADER_ID)))
                .willReturn(Map.of(LEADER_ID, new UserSummaryResponse(LEADER_ID, "홍길동", null)));

        TeamDetailResponse detail = teamService.getDetail(LEADER_ID, TEAM_ID);
        TeamMembersResponse members = teamService.getMembers(TEAM_ID);

        assertThat(detail.status()).isEqualTo(TeamStatus.DISBANDED);
        assertThat(members.members()).hasSize(1);
    }

    @Test
    @DisplayName("팀 상세·팀원 목록: 없는 팀은 TEAM_NOT_FOUND")
    void reads_unknownTeam_isNotFound() {
        givenNoTeam();

        assertErrorCode(() -> teamService.getDetail(LEADER_ID, TEAM_ID), ErrorCode.TEAM_NOT_FOUND);
        assertErrorCode(() -> teamService.getMembers(TEAM_ID), ErrorCode.TEAM_NOT_FOUND);
    }

    @Test
    @DisplayName("팀원 목록: ACTIVE 팀원과 사용자 요약을 합류 순으로 돌려준다")
    void getMembers_returnsActiveMembersWithUsers() {
        givenTeam(team());
        given(teamMemberRepository.findActiveMembers(TEAM_ID)).willReturn(List.of(
                member(LEADER_ID, TeamRole.LEADER, MemberStatus.ACTIVE),
                member(MEMBER_ID, TeamRole.MEMBER, MemberStatus.ACTIVE)));
        given(userService.getSummaries(List.of(LEADER_ID, MEMBER_ID)))
                .willReturn(Map.of(LEADER_ID, new UserSummaryResponse(LEADER_ID, "홍길동", null)));

        TeamMembersResponse response = teamService.getMembers(TEAM_ID);

        assertThat(response.members()).extracting(TeamMemberResponse::role)
                .containsExactly(TeamRole.LEADER, TeamRole.MEMBER);
        assertThat(response.members().get(0).user().nickname()).isEqualTo("홍길동");
        // 탈퇴한 회원은 사용자 요약이 없어 user가 null이다
        assertThat(response.members().get(1).user()).isNull();
    }

    // ---------- 타 도메인용 ----------

    @Test
    @DisplayName("요약 일괄 조회: 해체된 팀과 없는 id는 결과에서 빠지고 인원 수가 채워진다")
    void getSummaries_returnsOnlyActiveTeams() {
        given(teamRepository.findActiveByIdIn(List.of(TEAM_ID, 6L))).willReturn(List.of(team()));
        given(teamGenreRepository.findAllByTeamIdIn(List.of(TEAM_ID))).willReturn(List.of());
        given(teamMemberRepository.countActiveByTeamIds(List.of(TEAM_ID))).willReturn(List.of(count(TEAM_ID, 3)));

        Map<Long, TeamSummaryResponse> result = teamService.getSummaries(List.of(TEAM_ID, 6L));

        assertThat(result).containsOnlyKeys(TEAM_ID);
        assertThat(result.get(TEAM_ID).memberCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("요약 일괄 조회: List뿐 아니라 Set으로도 부를 수 있다")
    void getSummaries_acceptsAnyCollection() {
        given(teamRepository.findActiveByIdIn(java.util.Set.of(TEAM_ID))).willReturn(List.of(team()));
        given(teamGenreRepository.findAllByTeamIdIn(List.of(TEAM_ID))).willReturn(List.of());
        given(teamMemberRepository.countActiveByTeamIds(List.of(TEAM_ID))).willReturn(List.of());

        Map<Long, TeamSummaryResponse> result = teamService.getSummaries(java.util.Set.of(TEAM_ID));

        assertThat(result).containsOnlyKeys(TEAM_ID);
    }

    @Test
    @DisplayName("요약 일괄 조회: id가 없거나 비었으면 조회하지 않고 빈 Map이다")
    void getSummaries_withoutIds_returnsEmpty() {
        assertThat(teamService.getSummaries(null)).isEmpty();
        assertThat(teamService.getSummaries(List.of())).isEmpty();
        verify(teamRepository, never()).findActiveByIdIn(any());
    }

    @Test
    @DisplayName("내가 팀장인 팀: 활동 중인 팀의 요약을 장르·인원 수와 함께 돌려준다")
    void findLedTeams_returnsSummariesOfTeamsILead() {
        given(teamRepository.findActiveTeamsLedBy(LEADER_ID)).willReturn(List.of(team()));
        given(teamGenreRepository.findAllByTeamIdIn(List.of(TEAM_ID))).willReturn(List.of());
        given(teamMemberRepository.countActiveByTeamIds(List.of(TEAM_ID))).willReturn(List.of(count(TEAM_ID, 4)));

        List<TeamSummaryResponse> result = teamService.findLedTeams(LEADER_ID);

        assertThat(result).extracting(TeamSummaryResponse::id, TeamSummaryResponse::memberCount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(TEAM_ID, 4));
    }

    @Test
    @DisplayName("내가 팀장인 팀: 없으면 장르·인원 수를 조회하지 않고 빈 목록이다")
    void findLedTeams_withoutTeams_returnsEmpty() {
        given(teamRepository.findActiveTeamsLedBy(LEADER_ID)).willReturn(List.of());

        assertThat(teamService.findLedTeams(LEADER_ID)).isEmpty();
        verify(teamGenreRepository, never()).findAllByTeamIdIn(any());
    }

    private Team performanceTeam(Long id, Long performanceId) {
        Team team = Team.createForPerformance(performanceId, "공연팀", null, Region.SEOUL);
        ReflectionTestUtils.setField(team, "id", id);
        return team;
    }

    @Test
    @DisplayName("팀원 추가: 공연 팀이면 공연을 연 모임의 모임원인지 확인하고 통과하면 저장한다")
    void addMember_performanceTeam_checksGroupMember() {
        givenTeam(performanceTeam(TEAM_ID, 7L));
        givenMember(MEMBER_ID, null);

        teamService.addMember(TEAM_ID, MEMBER_ID, Instrument.DRUM);

        verify(performanceMemberService).ensureGroupMember(7L, MEMBER_ID);
        verify(teamMemberRepository).save(any(TeamMember.class));
    }

    @Test
    @DisplayName("팀원 추가: 공연을 연 모임의 모임원이 아니면 예외가 그대로 나가고 저장하지 않는다")
    void addMember_performanceTeam_rejectsNonGroupMember() {
        givenTeam(performanceTeam(TEAM_ID, 7L));
        willThrow(new BusinessException(ErrorCode.NOT_PERFORMANCE_GROUP_MEMBER))
                .given(performanceMemberService).ensureGroupMember(7L, MEMBER_ID);

        assertErrorCode(() -> teamService.addMember(TEAM_ID, MEMBER_ID, Instrument.DRUM),
                ErrorCode.NOT_PERFORMANCE_GROUP_MEMBER);
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("팀원 추가: 독립 팀은 모임원 확인을 하지 않는다")
    void addMember_independentTeam_skipsGroupMemberCheck() {
        givenTeam(team());
        givenMember(MEMBER_ID, null);

        teamService.addMember(TEAM_ID, MEMBER_ID, Instrument.DRUM);

        verify(performanceMemberService, never()).ensureGroupMember(any(), any());
    }

    @Test
    @DisplayName("공연 팀 나가기: 그 공연 팀들의 내 활동 팀원 행이 LEFT가 된다")
    void leavePerformanceTeams_marksMembershipsLeft() {
        given(teamRepository.findActiveByPerformanceIdInForUpdate(List.of(7L, 8L)))
                .willReturn(List.of(performanceTeam(5L, 7L), performanceTeam(6L, 8L)));
        TeamMember first = member(MEMBER_ID, TeamRole.MEMBER, MemberStatus.ACTIVE);
        TeamMember second = member(MEMBER_ID, TeamRole.MEMBER, MemberStatus.ACTIVE);
        given(teamMemberRepository.findActiveByUserIdAndTeamIdInForUpdate(MEMBER_ID, List.of(5L, 6L)))
                .willReturn(List.of(first, second));

        teamService.leavePerformanceTeams(MEMBER_ID, List.of(7L, 8L));

        assertThat(first.getStatus()).isEqualTo(MemberStatus.LEFT);
        assertThat(second.getStatus()).isEqualTo(MemberStatus.LEFT);
    }

    @Test
    @DisplayName("공연 팀 나가기: 한 팀이라도 팀장이면 LEADER_MUST_DELEGATE_FIRST이고 아무 행도 바꾸지 않는다")
    void leavePerformanceTeams_rejectsLeader_andChangesNothing() {
        given(teamRepository.findActiveByPerformanceIdInForUpdate(List.of(7L, 8L)))
                .willReturn(List.of(performanceTeam(5L, 7L), performanceTeam(6L, 8L)));
        TeamMember asMember = member(LEADER_ID, TeamRole.MEMBER, MemberStatus.ACTIVE);
        TeamMember asLeader = member(LEADER_ID, TeamRole.LEADER, MemberStatus.ACTIVE);
        given(teamMemberRepository.findActiveByUserIdAndTeamIdInForUpdate(LEADER_ID, List.of(5L, 6L)))
                .willReturn(List.of(asMember, asLeader));

        assertErrorCode(() -> teamService.leavePerformanceTeams(LEADER_ID, List.of(7L, 8L)),
                ErrorCode.LEADER_MUST_DELEGATE_FIRST);

        assertThat(asMember.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(asLeader.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("공연 팀 나가기: 공연 id가 없거나 활동 중인 팀이 없으면 팀원 행을 조회하지 않는다")
    void leavePerformanceTeams_withoutTeams_doesNothing() {
        given(teamRepository.findActiveByPerformanceIdInForUpdate(List.of(7L))).willReturn(List.of());

        teamService.leavePerformanceTeams(MEMBER_ID, null);
        teamService.leavePerformanceTeams(MEMBER_ID, List.of());
        teamService.leavePerformanceTeams(MEMBER_ID, List.of(7L));

        verify(teamMemberRepository, never()).findActiveByUserIdAndTeamIdInForUpdate(any(), any());
    }

    @Test
    @DisplayName("회원 탈퇴 나가기: 속한 활동 팀(공연 팀·독립 팀)의 내 팀원 행이 모두 LEFT가 된다")
    void leaveAllTeams_marksAllMembershipsLeft() {
        TeamMember inIndependent = member(MEMBER_ID, TeamRole.MEMBER, MemberStatus.ACTIVE);
        TeamMember inPerformance = member(MEMBER_ID, TeamRole.MEMBER, MemberStatus.ACTIVE);
        ReflectionTestUtils.setField(inIndependent, "teamId", 5L);
        ReflectionTestUtils.setField(inPerformance, "teamId", 6L);
        given(teamMemberRepository.findActiveMembershipsOf(MEMBER_ID)).willReturn(List.of(inIndependent, inPerformance));
        given(teamRepository.findActiveByIdInForUpdate(List.of(5L, 6L)))
                .willReturn(List.of(team(), performanceTeam(6L, 7L)));
        given(teamMemberRepository.findActiveByUserIdAndTeamIdInForUpdate(MEMBER_ID, List.of(5L, 6L)))
                .willReturn(List.of(inIndependent, inPerformance));

        teamService.leaveAllTeams(MEMBER_ID);

        assertThat(inIndependent.getStatus()).isEqualTo(MemberStatus.LEFT);
        assertThat(inPerformance.getStatus()).isEqualTo(MemberStatus.LEFT);
    }

    @Test
    @DisplayName("회원 탈퇴 나가기: 해체된 팀은 잠금 조회 결과에 없으므로 그 팀의 팀원 행은 그대로다")
    void leaveAllTeams_skipsDisbandedTeams() {
        TeamMember inDisbanded = member(MEMBER_ID, TeamRole.MEMBER, MemberStatus.ACTIVE);
        given(teamMemberRepository.findActiveMembershipsOf(MEMBER_ID)).willReturn(List.of(inDisbanded));
        given(teamRepository.findActiveByIdInForUpdate(List.of(TEAM_ID))).willReturn(List.of());

        teamService.leaveAllTeams(MEMBER_ID);

        assertThat(inDisbanded.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        verify(teamMemberRepository, never()).findActiveByUserIdAndTeamIdInForUpdate(any(), any());
    }

    @Test
    @DisplayName("회원 탈퇴 나가기: 팀장 행이 남아 있으면 LEADER_MUST_DELEGATE_FIRST이고 아무 행도 바꾸지 않는다")
    void leaveAllTeams_rejectsLeader_andChangesNothing() {
        TeamMember asMember = member(LEADER_ID, TeamRole.MEMBER, MemberStatus.ACTIVE);
        TeamMember asLeader = member(LEADER_ID, TeamRole.LEADER, MemberStatus.ACTIVE);
        ReflectionTestUtils.setField(asMember, "teamId", 6L);
        given(teamMemberRepository.findActiveMembershipsOf(LEADER_ID)).willReturn(List.of(asLeader, asMember));
        given(teamRepository.findActiveByIdInForUpdate(List.of(TEAM_ID, 6L)))
                .willReturn(List.of(team(), performanceTeam(6L, 7L)));
        given(teamMemberRepository.findActiveByUserIdAndTeamIdInForUpdate(LEADER_ID, List.of(TEAM_ID, 6L)))
                .willReturn(List.of(asLeader, asMember));

        assertErrorCode(() -> teamService.leaveAllTeams(LEADER_ID), ErrorCode.LEADER_MUST_DELEGATE_FIRST);

        assertThat(asLeader.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(asMember.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("회원 탈퇴 나가기: 속한 팀이 없으면 팀을 조회하지 않는다")
    void leaveAllTeams_withoutMemberships_doesNothing() {
        given(teamMemberRepository.findActiveMembershipsOf(MEMBER_ID)).willReturn(List.of());

        teamService.leaveAllTeams(MEMBER_ID);

        verify(teamRepository, never()).findActiveByIdInForUpdate(any());
    }

    @Test
    @DisplayName("요약 일괄 조회(해체 포함): 해체된 팀도 담고 없는 id만 빠진다")
    void getSummariesIncludingDisbanded_includesDisbandedTeams() {
        given(teamRepository.findAllById(List.of(TEAM_ID, 99L))).willReturn(List.of(disbandedTeam()));
        given(teamGenreRepository.findAllByTeamIdIn(List.of(TEAM_ID))).willReturn(List.of());
        given(teamMemberRepository.countActiveByTeamIds(List.of(TEAM_ID))).willReturn(List.of(count(TEAM_ID, 3)));

        Map<Long, TeamSummaryResponse> result = teamService.getSummariesIncludingDisbanded(List.of(TEAM_ID, 99L));

        assertThat(result).containsOnlyKeys(TEAM_ID);
        assertThat(result.get(TEAM_ID).status()).isEqualTo(TeamStatus.DISBANDED);
        assertThat(result.get(TEAM_ID).memberCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("요약 일괄 조회(해체 포함): id가 없거나 비었으면 조회하지 않고 빈 Map이다")
    void getSummariesIncludingDisbanded_withoutIds_returnsEmpty() {
        assertThat(teamService.getSummariesIncludingDisbanded(null)).isEmpty();
        assertThat(teamService.getSummariesIncludingDisbanded(List.of())).isEmpty();
        verify(teamRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("내 공연 팀: 팀 id·이름·공연 id·상태를 해체된 팀까지 그대로 돌려준다")
    void findMyPerformanceTeams_mapsTeamsIncludingDisbanded() {
        Team active = Team.createForPerformance(7L, "활동 중", null, Region.SEOUL);
        ReflectionTestUtils.setField(active, "id", 5L);
        Team disbanded = Team.createForPerformance(8L, "끝난 공연팀", null, Region.SEOUL);
        ReflectionTestUtils.setField(disbanded, "id", 6L);
        disbanded.disband();
        given(teamRepository.findMyPerformanceTeams(LEADER_ID)).willReturn(List.of(active, disbanded));

        List<PerformanceTeamResponse> result = teamService.findMyPerformanceTeams(LEADER_ID);

        assertThat(result).containsExactly(
                new PerformanceTeamResponse(5L, "활동 중", 7L, TeamStatus.ACTIVE),
                new PerformanceTeamResponse(6L, "끝난 공연팀", 8L, TeamStatus.DISBANDED));
    }

    @Test
    @DisplayName("공연의 팀 요약: 그 공연의 활성 팀을 장르·인원 수와 함께 돌려준다")
    void getActiveSummaries_returnsTeamsOfPerformance() {
        given(teamRepository.findActiveByPerformanceId(7L)).willReturn(List.of(team()));
        given(teamGenreRepository.findAllByTeamIdIn(List.of(TEAM_ID))).willReturn(List.of());
        given(teamMemberRepository.countActiveByTeamIds(List.of(TEAM_ID))).willReturn(List.of(count(TEAM_ID, 3)));

        List<TeamSummaryResponse> result = teamService.getActiveSummaries(7L);

        assertThat(result).containsExactly(
                new TeamSummaryResponse(TEAM_ID, "무명밴드", Region.SEOUL, List.of(), TeamStatus.ACTIVE, 3));
    }

    @Test
    @DisplayName("공연의 팀 요약: 팀이 없는(끝난) 공연은 빈 목록이고 추가 조회도 하지 않는다")
    void getActiveSummaries_withoutTeams_returnsEmptyWithoutExtraQueries() {
        given(teamRepository.findActiveByPerformanceId(7L)).willReturn(List.of());

        assertThat(teamService.getActiveSummaries(7L)).isEmpty();
        verify(teamGenreRepository, never()).findAllByTeamIdIn(any());
        verify(teamMemberRepository, never()).countActiveByTeamIds(any());
    }

    @Test
    @DisplayName("내 팀 id: 같은 공연의 여러 팀에 속해 있으면 모두 돌려주고, 없으면 빈 목록이다")
    void findMyTeamIds_returnsAllTeamIdsOfThePerformance() {
        given(teamRepository.findMyTeamIdsInPerformance(7L, MEMBER_ID)).willReturn(List.of(5L, 6L));
        given(teamRepository.findMyTeamIdsInPerformance(7L, 99L)).willReturn(List.of());

        assertThat(teamService.findMyTeamIds(7L, MEMBER_ID)).containsExactly(5L, 6L);
        assertThat(teamService.findMyTeamIds(7L, 99L)).isEmpty();
    }

    @Test
    @DisplayName("내 활동: 내가 속한 팀마다 팀 요약과 그 팀에서의 내 역할·악기를 합류 순으로 돌려준다")
    void findMemberships_returnsSummaryWithMyRoleAndInstrument() {
        Team second = Team.create("두번째 팀", null, Region.GYEONGGI);
        ReflectionTestUtils.setField(second, "id", 6L);
        TeamMember inSecond = TeamMember.create(6L, MEMBER_ID, TeamRole.LEADER, Instrument.DRUM);
        TeamMember inFirst = TeamMember.create(TEAM_ID, MEMBER_ID, TeamRole.MEMBER, Instrument.BASS);
        given(teamMemberRepository.findActiveMembershipsOf(MEMBER_ID)).willReturn(List.of(inSecond, inFirst));
        given(teamRepository.findActiveByIdIn(List.of(6L, TEAM_ID))).willReturn(List.of(team(), second));
        given(teamGenreRepository.findAllByTeamIdIn(List.of(TEAM_ID, 6L))).willReturn(List.of());
        given(teamMemberRepository.countActiveByTeamIds(List.of(TEAM_ID, 6L)))
                .willReturn(List.of(count(TEAM_ID, 4), count(6L, 1)));

        List<TeamMembershipResponse> result = teamService.findMemberships(MEMBER_ID);

        assertThat(result).extracting(r -> r.team().id()).containsExactly(6L, TEAM_ID);
        assertThat(result.get(0).role()).isEqualTo(TeamRole.LEADER);
        assertThat(result.get(0).instrument()).isEqualTo(Instrument.DRUM);
        assertThat(result.get(1).role()).isEqualTo(TeamRole.MEMBER);
        assertThat(result.get(1).team().memberCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("내 활동: 해체된 팀의 팀원 행은 결과에서 빠진다")
    void findMemberships_excludesMembershipsOfDisbandedTeams() {
        TeamMember inActive = TeamMember.create(TEAM_ID, MEMBER_ID, TeamRole.MEMBER, Instrument.BASS);
        TeamMember inDisbanded = TeamMember.create(6L, MEMBER_ID, TeamRole.MEMBER, Instrument.BASS);
        given(teamMemberRepository.findActiveMembershipsOf(MEMBER_ID)).willReturn(List.of(inActive, inDisbanded));
        // 해체된 팀(6L)은 활성 팀 조회에 나오지 않는다
        given(teamRepository.findActiveByIdIn(List.of(TEAM_ID, 6L))).willReturn(List.of(team()));
        given(teamGenreRepository.findAllByTeamIdIn(List.of(TEAM_ID))).willReturn(List.of());
        given(teamMemberRepository.countActiveByTeamIds(List.of(TEAM_ID))).willReturn(List.of(count(TEAM_ID, 2)));

        List<TeamMembershipResponse> result = teamService.findMemberships(MEMBER_ID);

        assertThat(result).extracting(r -> r.team().id()).containsExactly(TEAM_ID);
    }

    @Test
    @DisplayName("내 활동: 속한 팀이 없으면 빈 목록이고 팀·장르 조회도 하지 않는다")
    void findMemberships_withoutMemberships_returnsEmpty() {
        given(teamMemberRepository.findActiveMembershipsOf(MEMBER_ID)).willReturn(List.of());

        assertThat(teamService.findMemberships(MEMBER_ID)).isEmpty();
        verify(teamRepository, never()).findActiveByIdIn(any());
        verify(teamGenreRepository, never()).findAllByTeamIdIn(any());
    }

    @Test
    @DisplayName("팀원 추가: 처음 들어오는 사람은 MEMBER로 새로 저장된다")
    void addMember_newPerson_isSavedAsMember() {
        givenTeam(team());
        givenMember(MEMBER_ID, null);

        teamService.addMember(TEAM_ID, MEMBER_ID, Instrument.DRUM);

        ArgumentCaptor<TeamMember> saved = ArgumentCaptor.forClass(TeamMember.class);
        verify(teamMemberRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(MEMBER_ID);
        assertThat(saved.getValue().getRole()).isEqualTo(TeamRole.MEMBER);
        assertThat(saved.getValue().getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(saved.getValue().getInstrument()).isEqualTo(Instrument.DRUM);
    }

    @Test
    @DisplayName("팀원 추가: 탈퇴했거나 강퇴당했던 사람은 새 행 없이 기존 행이 되살아난다")
    void addMember_formerMember_isReactivated() {
        givenTeam(team());
        TeamMember left = member(MEMBER_ID, TeamRole.MEMBER, MemberStatus.LEFT);
        givenMember(MEMBER_ID, left);

        teamService.addMember(TEAM_ID, MEMBER_ID, Instrument.EL_GT);

        assertThat(left.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(left.getInstrument()).isEqualTo(Instrument.EL_GT);
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("팀원 추가: 강퇴당했던 사람도 다시 들어올 수 있다")
    void addMember_removedMember_canRejoin() {
        givenTeam(team());
        TeamMember removed = member(MEMBER_ID, TeamRole.MEMBER, MemberStatus.REMOVED);
        givenMember(MEMBER_ID, removed);

        teamService.addMember(TEAM_ID, MEMBER_ID, Instrument.BASS);

        assertThat(removed.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("팀원 추가: 이미 팀원이면 INVALID_STATE, 해체된 팀이면 INVALID_STATE, 없는 팀이면 TEAM_NOT_FOUND")
    void addMember_rejects_alreadyMember_disbandedTeam_unknownTeam() {
        givenTeam(team());
        givenMember(MEMBER_ID, member(MEMBER_ID, TeamRole.MEMBER, MemberStatus.ACTIVE));
        assertErrorCode(() -> teamService.addMember(TEAM_ID, MEMBER_ID, Instrument.BASS), ErrorCode.INVALID_STATE);

        givenTeam(disbandedTeam());
        assertErrorCode(() -> teamService.addMember(TEAM_ID, 3L, Instrument.BASS), ErrorCode.INVALID_STATE);

        givenNoTeam();
        assertErrorCode(() -> teamService.addMember(TEAM_ID, 3L, Instrument.BASS), ErrorCode.TEAM_NOT_FOUND);
    }

    @Test
    @DisplayName("공연 내 팀 생성: performanceId를 가진 팀이 만들어지고 만든 사람이 팀장이 된다")
    void createForPerformance_savesTeamWithPerformanceId() {
        given(teamRepository.save(any(Team.class))).willAnswer(inv -> {
            Team saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", TEAM_ID);
            return saved;
        });
        TeamMember leader = member(LEADER_ID, TeamRole.LEADER, MemberStatus.ACTIVE);
        given(teamGenreRepository.findAllByTeamIdIn(List.of(TEAM_ID))).willReturn(List.of());
        given(teamMemberRepository.findActiveMembers(TEAM_ID)).willReturn(List.of(leader));
        given(userService.getSummaries(List.of(LEADER_ID))).willReturn(Map.of());

        TeamDetailResponse response = teamService.createForPerformance(LEADER_ID, 7L, "공연팀", null,
                Region.SEOUL, List.of(Genre.BALLAD), Instrument.BASS);

        ArgumentCaptor<Team> team = ArgumentCaptor.forClass(Team.class);
        verify(teamRepository).save(team.capture());
        assertThat(team.getValue().getPerformanceId()).isEqualTo(7L);
        ArgumentCaptor<TeamMember> member = ArgumentCaptor.forClass(TeamMember.class);
        verify(teamMemberRepository).save(member.capture());
        assertThat(member.getValue().getRole()).isEqualTo(TeamRole.LEADER);
        assertThat(response.myRole()).isEqualTo(TeamRole.LEADER);
    }

    @Test
    @DisplayName("공연 종료 시 해체: 그 공연의 ACTIVE 팀이 모두 해체되고, 팀이 없어도 오류가 아니다")
    void disbandAllByPerformance_disbandsAllActiveTeams() {
        Team first = team();
        Team second = team();
        given(teamRepository.findActiveByPerformanceIdForUpdate(7L)).willReturn(List.of(first, second));
        given(teamRepository.findActiveByPerformanceIdForUpdate(8L)).willReturn(List.of());

        teamService.disbandAllByPerformance(7L);
        teamService.disbandAllByPerformance(8L);

        assertThat(first.getStatus()).isEqualTo(TeamStatus.DISBANDED);
        assertThat(second.getStatus()).isEqualTo(TeamStatus.DISBANDED);
    }

    @Test
    @DisplayName("공연 종료 시 해체: 끝난 공연의 기록을 남기려고 선택곡·셋리스트는 지우지 않는다")
    void disbandAllByPerformance_doesNotDeleteSetlistAndTeamSongs() {
        given(teamRepository.findActiveByPerformanceIdForUpdate(7L)).willReturn(List.of(team()));

        teamService.disbandAllByPerformance(7L);

        verify(setlistService, never()).deleteByTeam(any());
    }

    // ---------- recommendMembers ----------

    private TeamMember memberWith(Long userId, TeamRole role, Instrument instrument) {
        return TeamMember.create(TEAM_ID, userId, role, instrument);
    }

    private RecommendationCandidateResponse candidate(Long userId, Level level) {
        return new RecommendationCandidateResponse(userId, Region.SEOUL, List.of(Genre.ROCK), level);
    }

    private AiRecommendResponse.Item scored(Long userId, Double score, String... reasons) {
        return new AiRecommendResponse.Item(userId, score, List.of(reasons));
    }

    // 팀장 1명(보컬)만 있는 활동 중 팀에서 DRUM 후보 2명(30·31)이 있는 상황
    private void givenRecommendationSetup() {
        givenTeam(team());
        givenMember(LEADER_ID, memberWith(LEADER_ID, TeamRole.LEADER, Instrument.VOCAL));
        given(teamMemberRepository.findActiveMembers(TEAM_ID))
                .willReturn(List.of(memberWith(LEADER_ID, TeamRole.LEADER, Instrument.VOCAL)));
        // 후보 수를 바꾸는 테스트가 다시 덮어쓰므로 lenient다
        lenient().when(userService.findRecommendationCandidates(eq(Instrument.DRUM), eq(List.of(LEADER_ID)), eq(200)))
                .thenReturn(List.of(candidate(30L, Level.ADVANCED), candidate(31L, Level.NOVICE)));
        given(userService.getInstruments(List.of(LEADER_ID))).willReturn(Map.of(LEADER_ID, List.of(
                new UserInstrumentResponse(1L, Instrument.BASS, Level.NOVICE),
                new UserInstrumentResponse(2L, Instrument.VOCAL, Level.INTERMEDIATE))));
        lenient().when(aiMemberRecommendationClient.isConfigured()).thenReturn(true);
    }

    @Test
    @DisplayName("팀원 추천: 팀장이 아니면 403이고 후보 조회도 AI 호출도 하지 않는다")
    void recommendMembers_notLeader_forbidden() {
        givenTeam(team());
        givenMember(MEMBER_ID, member(MEMBER_ID, TeamRole.MEMBER, MemberStatus.ACTIVE));

        assertErrorCode(() -> teamService.recommendMembers(MEMBER_ID, TEAM_ID, Instrument.DRUM),
                ErrorCode.NOT_TEAM_LEADER);

        verify(userService, never()).findRecommendationCandidates(any(), any(), any(Integer.class));
        verify(aiMemberRecommendationClient, never()).recommend(any());
    }

    @Test
    @DisplayName("팀원 추천: 없는 팀은 404, 해체된 팀은 409다")
    void recommendMembers_missingOrDisbandedTeam() {
        givenNoTeam();
        assertErrorCode(() -> teamService.recommendMembers(LEADER_ID, TEAM_ID, Instrument.DRUM),
                ErrorCode.TEAM_NOT_FOUND);

        givenTeam(disbandedTeam());
        givenMember(LEADER_ID, member(LEADER_ID, TeamRole.LEADER, MemberStatus.ACTIVE));
        assertErrorCode(() -> teamService.recommendMembers(LEADER_ID, TEAM_ID, Instrument.DRUM),
                ErrorCode.INVALID_STATE);

        verify(aiMemberRecommendationClient, never()).recommend(any());
    }

    @Test
    @DisplayName("팀원 추천: 후보가 없으면 AI를 부르지 않고 빈 목록을 돌려준다")
    void recommendMembers_noCandidates_skipsAi() {
        givenTeam(team());
        givenMember(LEADER_ID, member(LEADER_ID, TeamRole.LEADER, MemberStatus.ACTIVE));
        given(teamMemberRepository.findActiveMembers(TEAM_ID))
                .willReturn(List.of(member(LEADER_ID, TeamRole.LEADER, MemberStatus.ACTIVE)));
        given(userService.findRecommendationCandidates(any(), any(), any(Integer.class))).willReturn(List.of());

        TeamMemberRecommendationResponse response =
                teamService.recommendMembers(LEADER_ID, TEAM_ID, Instrument.DRUM);

        assertThat(response.instrument()).isEqualTo(Instrument.DRUM);
        assertThat(response.candidates()).isEmpty();
        verify(aiMemberRecommendationClient, never()).recommend(any());
    }

    @Test
    @DisplayName("팀원 추천: AI에는 팀·찾는 악기·팀원(세션과 그 악기 실력)·후보를 ERD 값대로 보낸다")
    void recommendMembers_sendsTeamMembersAndCandidates() {
        givenRecommendationSetup();
        TeamGenre rock = TeamGenre.create(TEAM_ID, Genre.ROCK);
        ReflectionTestUtils.setField(rock, "id", 1L);
        given(teamGenreRepository.findAllByTeamIdIn(List.of(TEAM_ID))).willReturn(List.of(rock));
        given(aiMemberRecommendationClient.recommend(any())).willReturn(new AiRecommendResponse(List.of()));

        teamService.recommendMembers(LEADER_ID, TEAM_ID, Instrument.DRUM);

        ArgumentCaptor<AiRecommendRequest> captor = ArgumentCaptor.forClass(AiRecommendRequest.class);
        verify(aiMemberRecommendationClient).recommend(captor.capture());
        AiRecommendRequest sent = captor.getValue();
        assertThat(sent.team()).isEqualTo(new AiRecommendRequest.Team(TEAM_ID, Region.SEOUL, List.of(Genre.ROCK)));
        assertThat(sent.instrument()).isEqualTo(Instrument.DRUM);
        // 팀장은 이 팀에서 보컬이므로 실력도 보컬 것(INTERMEDIATE)이다 — 베이스 실력(NOVICE)이 아니다
        assertThat(sent.members()).containsExactly(
                new AiRecommendRequest.Member(LEADER_ID, Instrument.VOCAL, Level.INTERMEDIATE));
        assertThat(sent.candidates()).containsExactly(
                new AiRecommendRequest.Candidate(30L, Region.SEOUL, List.of(Genre.ROCK), Level.ADVANCED),
                new AiRecommendRequest.Candidate(31L, Region.SEOUL, List.of(Genre.ROCK), Level.NOVICE));
    }

    @Test
    @DisplayName("팀원 추천: 점수 높은 순으로 돌려주고, 보내지 않은 id·점수 없음·중복·탈퇴한 사용자는 버린다")
    void recommendMembers_sortsAndDropsInvalidItems() {
        givenRecommendationSetup();
        given(aiMemberRecommendationClient.recommend(any())).willReturn(new AiRecommendResponse(List.of(
                scored(31L, 0.40, "실력 NOVICE"),
                scored(30L, 0.87, "장르 일치: ROCK", "활동 지역 동일"),
                scored(30L, 0.10, "중복 항목"),
                scored(999L, 0.99, "보내지 않은 사용자"),
                new AiRecommendResponse.Item(31L, null, List.of("점수 없음")))));
        given(userService.getSummaries(any())).willReturn(Map.of(
                30L, new UserSummaryResponse(30L, "서준", null),
                31L, new UserSummaryResponse(31L, "민서", null)));

        TeamMemberRecommendationResponse response =
                teamService.recommendMembers(LEADER_ID, TEAM_ID, Instrument.DRUM);

        assertThat(response.candidates()).extracting(c -> c.user().id()).containsExactly(30L, 31L);
        assertThat(response.candidates().get(0).score()).isEqualTo(0.87);
        assertThat(response.candidates().get(0).reasons()).containsExactly("장르 일치: ROCK", "활동 지역 동일");

        // 31번이 탈퇴해 요약이 조회되지 않으면 결과에서 빠진다
        given(userService.getSummaries(any())).willReturn(Map.of(30L, new UserSummaryResponse(30L, "서준", null)));
        assertThat(teamService.recommendMembers(LEADER_ID, TEAM_ID, Instrument.DRUM).candidates())
                .extracting(c -> c.user().id()).containsExactly(30L);
    }

    @Test
    @DisplayName("팀원 추천: 최대 10명까지만 돌려주고 근거가 없으면 빈 목록이다")
    void recommendMembers_limitsToTenAndFillsEmptyReasons() {
        givenRecommendationSetup();
        List<RecommendationCandidateResponse> many = java.util.stream.LongStream.rangeClosed(100, 114)
                .mapToObj(id -> candidate(id, Level.NOVICE)).toList();
        given(userService.findRecommendationCandidates(eq(Instrument.DRUM), eq(List.of(LEADER_ID)), eq(200)))
                .willReturn(many);
        List<AiRecommendResponse.Item> items = many.stream()
                .map(c -> new AiRecommendResponse.Item(c.userId(), c.userId() / 1000.0, null)).toList();
        given(aiMemberRecommendationClient.recommend(any())).willReturn(new AiRecommendResponse(items));
        given(userService.getSummaries(any())).willAnswer(inv -> {
            java.util.Collection<Long> ids = inv.getArgument(0);
            return ids.stream().collect(java.util.stream.Collectors.toMap(id -> id,
                    id -> new UserSummaryResponse(id, "u" + id, null)));
        });

        TeamMemberRecommendationResponse response =
                teamService.recommendMembers(LEADER_ID, TEAM_ID, Instrument.DRUM);

        assertThat(response.candidates()).hasSize(10);
        assertThat(response.candidates().get(0).user().id()).isEqualTo(114L);
        assertThat(response.candidates().get(0).reasons()).isEmpty();
    }

    @Test
    @DisplayName("팀원 추천: AI 호출이 실패하거나 계약을 어기면 503 RECOMMENDATION_UNAVAILABLE이다")
    void recommendMembers_aiFailure_unavailable() {
        givenRecommendationSetup();
        given(aiMemberRecommendationClient.recommend(any()))
                .willThrow(new org.springframework.web.client.ResourceAccessException("timeout"))
                .willThrow(new IllegalStateException("candidates 없음"));

        assertErrorCode(() -> teamService.recommendMembers(LEADER_ID, TEAM_ID, Instrument.DRUM),
                ErrorCode.RECOMMENDATION_UNAVAILABLE);
        assertErrorCode(() -> teamService.recommendMembers(LEADER_ID, TEAM_ID, Instrument.DRUM),
                ErrorCode.RECOMMENDATION_UNAVAILABLE);
    }

    @Test
    @DisplayName("팀원 추천: AI 서버 주소가 설정되지 않았으면 호출하지 않고 503이다")
    void recommendMembers_notConfigured_unavailable() {
        givenRecommendationSetup();
        given(aiMemberRecommendationClient.isConfigured()).willReturn(false);

        assertErrorCode(() -> teamService.recommendMembers(LEADER_ID, TEAM_ID, Instrument.DRUM),
                ErrorCode.RECOMMENDATION_UNAVAILABLE);

        verify(aiMemberRecommendationClient, never()).recommend(any());
    }
}
