package com.moyeorock.domain.performance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moyeorock.domain.group.dto.response.GroupBriefResponse;
import com.moyeorock.domain.group.service.GroupMemberService;
import com.moyeorock.domain.performance.dto.request.PerformanceCreateRequest;
import com.moyeorock.domain.performance.dto.request.PerformanceStatusUpdateRequest;
import com.moyeorock.domain.performance.dto.request.PerformanceTeamCreateRequest;
import com.moyeorock.domain.performance.dto.request.PerformanceUpdateRequest;
import com.moyeorock.domain.performance.dto.request.SetlistConfirmRequest;
import com.moyeorock.domain.performance.dto.request.SetlistSubmitRequest;
import com.moyeorock.domain.performance.dto.response.PerformanceActivityResponse;
import com.moyeorock.domain.performance.dto.response.PerformanceDetailResponse;
import com.moyeorock.domain.performance.dto.response.PerformanceStatusResponse;
import com.moyeorock.domain.performance.dto.response.SetlistResponse;
import com.moyeorock.domain.performance.entity.Performance;
import com.moyeorock.domain.performance.enums.PerformancePeriod;
import com.moyeorock.domain.performance.enums.PerformanceStatus;
import com.moyeorock.domain.performance.repository.PerformanceRepository;
import com.moyeorock.domain.setlist.dto.response.SetlistItemResponse;
import com.moyeorock.domain.setlist.service.SetlistService;
import com.moyeorock.domain.song.dto.response.SongSummaryResponse;
import com.moyeorock.domain.team.dto.response.PerformanceTeamResponse;
import com.moyeorock.domain.team.dto.response.TeamDetailResponse;
import com.moyeorock.domain.team.dto.response.TeamSummaryResponse;
import com.moyeorock.domain.team.enums.TeamRole;
import com.moyeorock.domain.team.enums.TeamStatus;
import com.moyeorock.domain.team.service.TeamMemberService;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import com.moyeorock.domain.user.service.UserService;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PerformanceServiceTest {

    private static final Long GROUP_ID = 1L;
    private static final Long OWNER_ID = 10L;
    private static final Long MEMBER_ID = 20L;
    private static final Long PERFORMANCE_ID = 100L;
    // 생성·수정이 지난 일시를 막아서 테스트 일시는 실행 날짜 기준으로 잡는다 — 고정 날짜는 그날이 지나면 테스트가 깨진다.
    private static final LocalDateTime PERFORMED_AT = LocalDate.now().plusMonths(1).atTime(18, 0);
    private static final LocalDateTime PAST = LocalDate.now().minusMonths(1).atTime(18, 0);
    private static final GroupBriefResponse GROUP = new GroupBriefResponse(GROUP_ID, "홍대 밴드부", "cover.jpg");
    private static final UserSummaryResponse CREATOR = new UserSummaryResponse(OWNER_ID, "모임장", null);

    @Mock
    private PerformanceRepository performanceRepository;

    @Mock
    private GroupMemberService groupMemberService;

    @Mock
    private UserService userService;

    @Mock
    private TeamService teamService;

    @Mock
    private SetlistService setlistService;

    @Mock
    private TeamMemberService teamMemberService;

    @InjectMocks
    private PerformanceService performanceService;

    @Test
    @DisplayName("모임장이 만든 공연은 PLANNED로 저장되고 canEdit=true인 상세 응답을 돌려준다")
    void create_savesPlannedPerformance() {
        when(performanceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        givenDetailParts();

        PerformanceDetailResponse response = performanceService.create(OWNER_ID, GROUP_ID, createRequest());

        ArgumentCaptor<Performance> captor = ArgumentCaptor.forClass(Performance.class);
        verify(performanceRepository).save(captor.capture());
        assertThat(captor.getValue().getGroupId()).isEqualTo(GROUP_ID);
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(OWNER_ID);
        assertThat(response.status()).isEqualTo(PerformanceStatus.PLANNED);
        assertThat(response.title()).isEqualTo("2026 정기공연");
        assertThat(response.venue()).isEqualTo("홍대 롤링홀");
        assertThat(response.group()).isEqualTo(GROUP);
        assertThat(response.createdBy()).isEqualTo(CREATOR);
        assertThat(response.canEdit()).isTrue();
    }

    @Test
    @DisplayName("모임장이 아니면 공연을 만들 수 없고 저장하지 않는다")
    void create_throws_whenNotOwner() {
        doThrow(new BusinessException(ErrorCode.NOT_GROUP_OWNER))
                .when(groupMemberService).ensureOwner(GROUP_ID, MEMBER_ID);

        assertThatThrownBy(() -> performanceService.create(MEMBER_ID, GROUP_ID, createRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_GROUP_OWNER);
        verify(performanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("모임원이 아니면 공연 목록을 볼 수 없고 공연을 조회하지 않는다")
    void getList_throws_whenNotMember() {
        doThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER))
                .when(groupMemberService).ensureActiveMember(GROUP_ID, MEMBER_ID);

        assertThatThrownBy(() -> performanceService.getList(MEMBER_ID, GROUP_ID, null, PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
        verifyNoInteractions(performanceRepository);
    }

    @Test
    @DisplayName("공연 목록은 클라이언트 sort를 버리고 page·size만 넘긴다 — 정렬은 Repository 메서드에 고정돼 있다")
    void getList_dropsClientSort() {
        when(performanceRepository.findByGroupIdOrderByPerformedAtDescIdDesc(any(), any())).thenReturn(Page.empty());

        performanceService.getList(MEMBER_ID, GROUP_ID, null, PageRequest.of(1, 10, Sort.by("title")));

        verify(performanceRepository).findByGroupIdOrderByPerformedAtDescIdDesc(GROUP_ID, PageRequest.of(1, 10));
    }

    @Test
    @DisplayName("period=UPCOMING이면 PLANNED·RECRUITING만 공연일 가까운 순으로 조회한다")
    void getList_withUpcoming_queriesPlannedAndRecruitingAscending() {
        when(performanceRepository.findByGroupIdAndStatusInOrderByPerformedAtAscIdAsc(any(), any(), any()))
                .thenReturn(Page.empty());

        performanceService.getList(MEMBER_ID, GROUP_ID, PerformancePeriod.UPCOMING, PageRequest.of(0, 20));

        verify(performanceRepository).findByGroupIdAndStatusInOrderByPerformedAtAscIdAsc(GROUP_ID,
                List.of(PerformanceStatus.PLANNED, PerformanceStatus.RECRUITING), PageRequest.of(0, 20));
    }

    @Test
    @DisplayName("period=PAST면 DONE·CANCELED만 공연일 최근 순으로 조회한다")
    void getList_withPast_queriesDoneAndCanceledDescending() {
        when(performanceRepository.findByGroupIdAndStatusInOrderByPerformedAtDescIdDesc(any(), any(), any()))
                .thenReturn(Page.empty());

        performanceService.getList(MEMBER_ID, GROUP_ID, PerformancePeriod.PAST, PageRequest.of(0, 20));

        verify(performanceRepository).findByGroupIdAndStatusInOrderByPerformedAtDescIdDesc(GROUP_ID,
                List.of(PerformanceStatus.DONE, PerformanceStatus.CANCELED), PageRequest.of(0, 20));
    }

    @Test
    @DisplayName("없는 공연이면 PERFORMANCE_NOT_FOUND이고 모임 권한은 묻지 않는다")
    void getDetail_throws_whenPerformanceMissing() {
        when(performanceRepository.findById(PERFORMANCE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> performanceService.getDetail(MEMBER_ID, PERFORMANCE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PERFORMANCE_NOT_FOUND);
        verifyNoInteractions(groupMemberService);
    }

    @Test
    @DisplayName("동아리 홈의 다가오는 공연은 UPCOMING 상태(PLANNED·RECRUITING)로 앞의 3건을 요약으로 준다")
    void findUpcoming_mapsTop3UpcomingPerformances() {
        Performance performance = Performance.create(GROUP_ID, OWNER_ID, "2026 정기공연", null, PERFORMED_AT,
                "홍대 롤링홀", "poster.jpg");
        when(performanceRepository.findTop3ByGroupIdAndStatusInOrderByPerformedAtAscIdAsc(
                GROUP_ID, PerformancePeriod.UPCOMING.getStatuses())).thenReturn(List.of(performance));

        assertThat(performanceService.findUpcoming(GROUP_ID))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.title()).isEqualTo("2026 정기공연");
                    assertThat(summary.status()).isEqualTo(PerformanceStatus.PLANNED);
                });
        verifyNoInteractions(groupMemberService);
    }

    @Test
    @DisplayName("모임원이 아니면 공연 상세를 볼 수 없다")
    void getDetail_throws_whenNotMember() {
        givenPerformance();
        doThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER))
                .when(groupMemberService).ensureActiveMember(GROUP_ID, MEMBER_ID);

        assertThatThrownBy(() -> performanceService.getDetail(MEMBER_ID, PERFORMANCE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
    }

    @Test
    @DisplayName("일반 모임원이 보는 공연 상세는 canEdit=false다")
    void getDetail_returnsCanEditFalse_forMember() {
        givenPerformance();
        when(groupMemberService.isOwner(GROUP_ID, MEMBER_ID)).thenReturn(false);
        givenDetailParts();

        PerformanceDetailResponse response = performanceService.getDetail(MEMBER_ID, PERFORMANCE_ID);

        verify(groupMemberService).ensureActiveMember(GROUP_ID, MEMBER_ID);
        assertThat(response.canEdit()).isFalse();
        assertThat(response.group()).isEqualTo(GROUP);
    }

    @Test
    @DisplayName("모임장이 보는 공연 상세는 canEdit=true다")
    void getDetail_returnsCanEditTrue_forOwner() {
        givenPerformance();
        when(groupMemberService.isOwner(GROUP_ID, OWNER_ID)).thenReturn(true);
        givenDetailParts();

        assertThat(performanceService.getDetail(OWNER_ID, PERFORMANCE_ID).canEdit()).isTrue();
    }

    @Test
    @DisplayName("공연 상세의 참여 팀과 내가 속한 팀 id는 TeamService에서 받는다")
    void getDetail_fillsTeamsAndMyTeamIds_fromTeamService() {
        Performance performance = givenPerformance();
        ReflectionTestUtils.setField(performance, "id", PERFORMANCE_ID);
        when(groupMemberService.isOwner(GROUP_ID, MEMBER_ID)).thenReturn(false);
        givenDetailParts();
        TeamSummaryResponse team = new TeamSummaryResponse(5L, "무명밴드", Region.SEOUL, List.of(Genre.ROCK),
                TeamStatus.ACTIVE, 3);
        when(teamService.getActiveSummaries(PERFORMANCE_ID)).thenReturn(List.of(team));
        when(teamService.findMyTeamIds(PERFORMANCE_ID, MEMBER_ID)).thenReturn(List.of(5L));

        PerformanceDetailResponse response = performanceService.getDetail(MEMBER_ID, PERFORMANCE_ID);

        assertThat(response.teams()).containsExactly(team);
        assertThat(response.myTeamIds()).containsExactly(5L);
    }

    @Test
    @DisplayName("공연을 만든 사람이 탈퇴했으면 createdBy는 null이다")
    void getDetail_returnsNullCreatedBy_whenCreatorWithdrawn() {
        givenPerformance();
        when(groupMemberService.isOwner(GROUP_ID, MEMBER_ID)).thenReturn(false);
        when(groupMemberService.getGroupBrief(GROUP_ID)).thenReturn(GROUP);
        when(userService.getSummaries(List.of(OWNER_ID))).thenReturn(Map.of());

        assertThat(performanceService.getDetail(MEMBER_ID, PERFORMANCE_ID).createdBy()).isNull();
    }

    @Test
    @DisplayName("공연의 created_by가 비어 있으면 createdBy는 null이고 사용자 조회를 하지 않는다")
    void getDetail_returnsNullCreatedBy_whenCreatedByMissing() {
        Performance performance = Performance.create(GROUP_ID, null, "2026 정기공연", "설명", PERFORMED_AT,
                "홍대 롤링홀", "poster.jpg");
        when(performanceRepository.findById(PERFORMANCE_ID)).thenReturn(Optional.of(performance));
        when(groupMemberService.isOwner(GROUP_ID, MEMBER_ID)).thenReturn(false);
        when(groupMemberService.getGroupBrief(GROUP_ID)).thenReturn(GROUP);

        assertThat(performanceService.getDetail(MEMBER_ID, PERFORMANCE_ID).createdBy()).isNull();
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("모임장이 공연을 고치면 값이 바뀌고 canEdit=true인 상세 응답을 돌려준다")
    void update_changesFields_whenOwner() {
        Performance performance = givenPerformance();
        givenDetailParts();

        PerformanceDetailResponse response = performanceService.update(OWNER_ID, PERFORMANCE_ID, updateRequest());

        assertThat(performance.getTitle()).isEqualTo("2026 정기공연 (연기)");
        assertThat(performance.getVenue()).isEqualTo("신촌 클럽");
        assertThat(performance.getPosterImage()).isNull();
        assertThat(response.title()).isEqualTo("2026 정기공연 (연기)");
        assertThat(response.canEdit()).isTrue();
    }

    @Test
    @DisplayName("모임장이 아니면 공연을 고칠 수 없고 값도 그대로다")
    void update_throws_whenNotOwner() {
        Performance performance = givenPerformance();
        doThrow(new BusinessException(ErrorCode.NOT_GROUP_OWNER))
                .when(groupMemberService).ensureOwner(GROUP_ID, MEMBER_ID);

        assertThatThrownBy(() -> performanceService.update(MEMBER_ID, PERFORMANCE_ID, updateRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_GROUP_OWNER);
        assertThat(performance.getTitle()).isEqualTo("2026 정기공연");
    }

    @Test
    @DisplayName("끝난 공연(DONE·CANCELED)은 고칠 수 없다(INVALID_STATE) — 값은 그대로다")
    void update_throws_whenFinished() {
        Performance performance = givenPerformance();

        for (PerformanceStatus finished : List.of(PerformanceStatus.DONE, PerformanceStatus.CANCELED)) {
            performance.changeStatus(finished);
            assertThatThrownBy(() -> performanceService.update(OWNER_ID, PERFORMANCE_ID, updateRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_STATE);
        }
        assertThat(performance.getTitle()).isEqualTo("2026 정기공연");
    }

    @Test
    @DisplayName("공연 일시를 지난 시각으로 바꾸면 VALIDATION_FAILED이고 값은 그대로다")
    void update_throws_whenRescheduledToPast() {
        Performance performance = givenPerformance();
        PerformanceUpdateRequest request = new PerformanceUpdateRequest("2026 정기공연 (연기)", null, PAST, "신촌 클럽",
                null);

        assertThatThrownBy(() -> performanceService.update(OWNER_ID, PERFORMANCE_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(performance.getTitle()).isEqualTo("2026 정기공연");
        assertThat(performance.getPerformedAt()).isEqualTo(PERFORMED_AT);
    }

    @Test
    @DisplayName("일시를 그대로 두면 일시가 지났어도 끝나지 않은 공연은 고칠 수 있다")
    void update_allowsPastPerformance_whenPerformedAtUnchanged() {
        Performance performance = givenPerformance(PAST);
        givenDetailParts();
        PerformanceUpdateRequest request = new PerformanceUpdateRequest("2020 정기공연 후기", "다녀왔습니다.", PAST,
                "홍대 롤링홀", null);

        performanceService.update(OWNER_ID, PERFORMANCE_ID, request);

        assertThat(performance.getTitle()).isEqualTo("2020 정기공연 후기");
        assertThat(performance.getDescription()).isEqualTo("다녀왔습니다.");
    }

    @Test
    @DisplayName("없는 공연을 고치려 하면 PERFORMANCE_NOT_FOUND다")
    void update_throws_whenPerformanceMissing() {
        when(performanceRepository.findById(PERFORMANCE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> performanceService.update(OWNER_ID, PERFORMANCE_ID, updateRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PERFORMANCE_NOT_FOUND);
        verifyNoInteractions(groupMemberService);
    }

    @Test
    @DisplayName("내 활동 공연은 활동 중인 내 팀이 나가는 다가올 공연을 저장소 순서대로, 한 공연에 내 팀이 여럿이면 팀마다 한 줄로 준다")
    void findActivitiesByUserId_listsUpcomingPerformancesPerMyTeam() {
        when(teamService.findMyPerformanceTeams(MEMBER_ID)).thenReturn(List.of(
                new PerformanceTeamResponse(1L, "무명밴드", 100L, TeamStatus.ACTIVE),
                new PerformanceTeamResponse(2L, "두 번째 밴드", 100L, TeamStatus.ACTIVE),
                new PerformanceTeamResponse(3L, "스스로 해체한 팀", 101L, TeamStatus.DISBANDED),
                new PerformanceTeamResponse(4L, "가을 팀", 102L, TeamStatus.ACTIVE)));
        // 해체된 팀만 있는 101번 공연은 조회 대상에서 빠진다. 정렬은 저장소 메서드 이름(공연일 가까운 순)에 맡긴다
        when(performanceRepository.findByIdInAndStatusInOrderByPerformedAtAscIdAsc(
                Set.of(100L, 102L), PerformancePeriod.UPCOMING.getStatuses()))
                .thenReturn(List.of(performanceWithId(102L, "가을 공연"), performanceWithId(100L, "겨울 공연")));
        when(groupMemberService.getNames(List.of(GROUP_ID))).thenReturn(Map.of(GROUP_ID, "홍대 밴드부"));

        List<PerformanceActivityResponse> activities = performanceService.findActivitiesByUserId(MEMBER_ID);

        assertThat(activities)
                .extracting(PerformanceActivityResponse::id, PerformanceActivityResponse::title,
                        PerformanceActivityResponse::groupName, PerformanceActivityResponse::teamName)
                .containsExactly(
                        tuple(102L, "가을 공연", "홍대 밴드부", "가을 팀"),
                        tuple(100L, "겨울 공연", "홍대 밴드부", "무명밴드"),
                        tuple(100L, "겨울 공연", "홍대 밴드부", "두 번째 밴드"));
    }

    @Test
    @DisplayName("해체된 모임의 공연은 모임 이름이 오지 않으므로 내 활동 공연에서 빠진다")
    void findActivitiesByUserId_skipsPerformancesOfDisbandedGroups() {
        when(teamService.findMyPerformanceTeams(MEMBER_ID)).thenReturn(List.of(
                new PerformanceTeamResponse(1L, "무명밴드", 100L, TeamStatus.ACTIVE)));
        when(performanceRepository.findByIdInAndStatusInOrderByPerformedAtAscIdAsc(
                Set.of(100L), PerformancePeriod.UPCOMING.getStatuses()))
                .thenReturn(List.of(performanceWithId(100L, "겨울 공연")));
        when(groupMemberService.getNames(List.of(GROUP_ID))).thenReturn(Map.of());

        assertThat(performanceService.findActivitiesByUserId(MEMBER_ID)).isEmpty();
    }

    @Test
    @DisplayName("활동 중인 내 공연 팀이 없으면 공연·모임을 조회하지 않고 빈 목록을 준다")
    void findActivitiesByUserId_returnsEmpty_whenNoActivePerformanceTeam() {
        when(teamService.findMyPerformanceTeams(MEMBER_ID)).thenReturn(List.of(
                new PerformanceTeamResponse(3L, "스스로 해체한 팀", 101L, TeamStatus.DISBANDED)));

        assertThat(performanceService.findActivitiesByUserId(MEMBER_ID)).isEmpty();
        verifyNoInteractions(performanceRepository, groupMemberService);
    }

    @Test
    @DisplayName("모임장이 공연을 모집 중으로 바꾸면 상태만 바뀌고 팀은 해체하지 않는다")
    void changeStatus_toRecruiting_doesNotDisbandTeams() {
        Performance performance = givenPerformance();

        PerformanceStatusResponse response = performanceService.changeStatus(OWNER_ID, PERFORMANCE_ID,
                statusRequest(PerformanceStatus.RECRUITING));

        assertThat(performance.getStatus()).isEqualTo(PerformanceStatus.RECRUITING);
        assertThat(response.status()).isEqualTo(PerformanceStatus.RECRUITING);
        verifyNoInteractions(teamService);
    }

    @Test
    @DisplayName("공연을 종료하면 소속 팀을 모두 해체한다")
    void changeStatus_toDone_disbandsTeams() {
        Performance performance = givenPerformance();

        performanceService.changeStatus(OWNER_ID, PERFORMANCE_ID, statusRequest(PerformanceStatus.DONE));

        assertThat(performance.getStatus()).isEqualTo(PerformanceStatus.DONE);
        verify(teamService).disbandAllByPerformance(PERFORMANCE_ID);
    }

    @Test
    @DisplayName("공연을 취소해도 소속 팀을 모두 해체한다")
    void changeStatus_toCanceled_disbandsTeams() {
        Performance performance = givenPerformance();

        performanceService.changeStatus(OWNER_ID, PERFORMANCE_ID, statusRequest(PerformanceStatus.CANCELED));

        assertThat(performance.getStatus()).isEqualTo(PerformanceStatus.CANCELED);
        verify(teamService).disbandAllByPerformance(PERFORMANCE_ID);
    }

    @Test
    @DisplayName("끝난 공연은 상태를 바꿀 수 없고(INVALID_STATE) 팀 해체도 다시 하지 않는다")
    void changeStatus_throws_whenFinished() {
        Performance performance = givenPerformance();
        performance.changeStatus(PerformanceStatus.DONE);

        assertThatThrownBy(() -> performanceService.changeStatus(OWNER_ID, PERFORMANCE_ID,
                statusRequest(PerformanceStatus.PLANNED)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_STATE);
        assertThat(performance.getStatus()).isEqualTo(PerformanceStatus.DONE);
        verifyNoInteractions(teamService);
    }

    @Test
    @DisplayName("모임장이 아니면 상태를 바꿀 수 없고 팀도 해체하지 않는다")
    void changeStatus_throws_whenNotOwner() {
        Performance performance = givenPerformance();
        doThrow(new BusinessException(ErrorCode.NOT_GROUP_OWNER))
                .when(groupMemberService).ensureOwner(GROUP_ID, MEMBER_ID);

        assertThatThrownBy(() -> performanceService.changeStatus(MEMBER_ID, PERFORMANCE_ID,
                statusRequest(PerformanceStatus.DONE)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_GROUP_OWNER);
        assertThat(performance.getStatus()).isEqualTo(PerformanceStatus.PLANNED);
        verifyNoInteractions(teamService);
    }

    @Test
    @DisplayName("없는 공연의 상태를 바꾸려 하면 PERFORMANCE_NOT_FOUND다")
    void changeStatus_throws_whenPerformanceMissing() {
        when(performanceRepository.findById(PERFORMANCE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> performanceService.changeStatus(OWNER_ID, PERFORMANCE_ID,
                statusRequest(PerformanceStatus.DONE)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PERFORMANCE_NOT_FOUND);
        verifyNoInteractions(groupMemberService, teamService);
    }

    @Test
    @DisplayName("모임원이 공연 안에 팀을 만들면 요청 값을 그대로 TeamService에 넘기고 팀 상세를 돌려준다")
    void createTeam_byMember_createsTeamViaTeamService() {
        givenPerformance();
        TeamDetailResponse team = new TeamDetailResponse(5L, "무명밴드", "발라드 위주로 갑니다.", Region.SEOUL,
                List.of(Genre.BALLAD, Genre.ROCK), TeamStatus.ACTIVE, null, null, 1, List.of(), List.of(), TeamRole.LEADER, null);
        when(teamService.createForPerformance(MEMBER_ID, PERFORMANCE_ID, "무명밴드", "발라드 위주로 갑니다.",
                Region.SEOUL, List.of(Genre.BALLAD, Genre.ROCK), Instrument.BASS)).thenReturn(team);

        TeamDetailResponse response = performanceService.createTeam(MEMBER_ID, PERFORMANCE_ID, teamCreateRequest());

        verify(groupMemberService).ensureActiveMember(GROUP_ID, MEMBER_ID);
        assertThat(response).isSameAs(team);
    }

    @Test
    @DisplayName("모임원이 아니면 공연 안에 팀을 만들 수 없다")
    void createTeam_throws_whenNotMember() {
        givenPerformance();
        doThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER))
                .when(groupMemberService).ensureActiveMember(GROUP_ID, MEMBER_ID);

        assertThatThrownBy(() -> performanceService.createTeam(MEMBER_ID, PERFORMANCE_ID, teamCreateRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
        verifyNoInteractions(teamService);
    }

    @Test
    @DisplayName("끝난 공연(취소)에는 팀을 만들 수 없다(INVALID_STATE)")
    void createTeam_throws_whenFinished() {
        givenPerformance().changeStatus(PerformanceStatus.CANCELED);

        assertThatThrownBy(() -> performanceService.createTeam(MEMBER_ID, PERFORMANCE_ID, teamCreateRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_STATE);
        verifyNoInteractions(teamService);
    }

    @Test
    @DisplayName("없는 공연에 팀을 만들려 하면 PERFORMANCE_NOT_FOUND다")
    void createTeam_throws_whenPerformanceMissing() {
        when(performanceRepository.findById(PERFORMANCE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> performanceService.createTeam(MEMBER_ID, PERFORMANCE_ID, teamCreateRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PERFORMANCE_NOT_FOUND);
        verifyNoInteractions(groupMemberService, teamService);
    }

    @Test
    @DisplayName("모임원은 공연 셋리스트를 본다 — 확정 전이면 넘겨진 곡 전체 조회를 쓴다")
    void getSetlist_returnsSubmittedSongs_forMember() {
        Performance performance = givenPerformance();
        ReflectionTestUtils.setField(performance, "id", PERFORMANCE_ID);
        SetlistItemResponse item = new SetlistItemResponse(5L, "무명밴드", new SongSummaryResponse(88L, "Bohemian Rhapsody", "Queen"), null);
        when(setlistService.getSetlist(PERFORMANCE_ID, false)).thenReturn(List.of(item));

        SetlistResponse response = performanceService.getSetlist(MEMBER_ID, PERFORMANCE_ID);

        verify(groupMemberService).ensureActiveMember(GROUP_ID, MEMBER_ID);
        assertThat(response).isEqualTo(new SetlistResponse(PERFORMANCE_ID, false, List.of(item)));
    }

    @Test
    @DisplayName("모임원이 아니면 공연 셋리스트를 볼 수 없고, 없는 공연이면 모임 권한도 묻지 않는다")
    void getSetlist_throws_whenNotMemberOrMissing() {
        givenPerformance();
        doThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER))
                .when(groupMemberService).ensureActiveMember(GROUP_ID, MEMBER_ID);

        assertThatThrownBy(() -> performanceService.getSetlist(MEMBER_ID, PERFORMANCE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
        verifyNoInteractions(setlistService);
    }

    @Test
    @DisplayName("공연 팀 팀장이 넘기면 행 처리를 SetlistService에 맡기고 넘긴 뒤의 셋리스트를 돌려준다")
    void submitSetlist_delegatesToSetlistService() {
        givenPerformanceForUpdate();
        when(teamMemberService.getPerformanceId(5L)).thenReturn(PERFORMANCE_ID);
        when(setlistService.getSetlist(PERFORMANCE_ID, false)).thenReturn(List.of());

        SetlistResponse response = performanceService.submitSetlist(MEMBER_ID, PERFORMANCE_ID, 5L,
                new SetlistSubmitRequest(List.of(88L)));

        verify(teamMemberService).ensureLeader(5L, MEMBER_ID);
        verify(setlistService).submit(PERFORMANCE_ID, 5L, List.of(88L));
        assertThat(response.confirmed()).isFalse();
    }

    @Test
    @DisplayName("팀장이 아니면 넘길 수 없고 행을 건드리지 않는다")
    void submitSetlist_throws_whenNotLeader() {
        givenPerformanceForUpdate();
        doThrow(new BusinessException(ErrorCode.NOT_TEAM_LEADER)).when(teamMemberService).ensureLeader(5L, MEMBER_ID);

        assertThatThrownBy(() -> performanceService.submitSetlist(MEMBER_ID, PERFORMANCE_ID, 5L,
                new SetlistSubmitRequest(List.of(88L))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_TEAM_LEADER);
        verifyNoInteractions(setlistService);
    }

    @Test
    @DisplayName("이 공연 소속이 아닌 팀(독립 팀 포함)이면 TEAM_NOT_FOUND다")
    void submitSetlist_throws_whenTeamNotInPerformance() {
        givenPerformanceForUpdate();
        when(teamMemberService.getPerformanceId(5L)).thenReturn(999L);

        assertThatThrownBy(() -> performanceService.submitSetlist(MEMBER_ID, PERFORMANCE_ID, 5L,
                new SetlistSubmitRequest(List.of(88L))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TEAM_NOT_FOUND);
        verifyNoInteractions(setlistService);
    }

    @Test
    @DisplayName("셋리스트가 확정됐으면 넘길 수 없다(INVALID_STATE)")
    void submitSetlist_throws_whenConfirmed() {
        Performance performance = givenPerformanceForUpdate();
        ReflectionTestUtils.setField(performance, "setlistConfirmedAt", LocalDateTime.now());
        when(teamMemberService.getPerformanceId(5L)).thenReturn(PERFORMANCE_ID);

        assertThatThrownBy(() -> performanceService.submitSetlist(MEMBER_ID, PERFORMANCE_ID, 5L,
                new SetlistSubmitRequest(List.of(88L))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_STATE);
        verifyNoInteractions(setlistService);
    }

    @Test
    @DisplayName("없는 공연에 넘기면 PERFORMANCE_NOT_FOUND이고 팀 확인도 하지 않는다")
    void submitSetlist_throws_whenPerformanceMissing() {
        when(performanceRepository.findByIdForUpdate(PERFORMANCE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> performanceService.submitSetlist(MEMBER_ID, PERFORMANCE_ID, 5L,
                new SetlistSubmitRequest(List.of(88L))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PERFORMANCE_NOT_FOUND);
        verifyNoInteractions(teamMemberService, setlistService);
    }

    @Test
    @DisplayName("모임장이 확정하면 항목을 SetlistService 값으로 바꿔 맡기고, 확정 시각을 찍어 확정곡을 돌려준다")
    void confirmSetlist_delegatesAndMarksConfirmed() {
        Performance performance = givenPerformanceForUpdate();
        when(teamMemberService.getPerformanceId(9L)).thenReturn(PERFORMANCE_ID);
        when(teamMemberService.getPerformanceId(5L)).thenReturn(PERFORMANCE_ID);
        when(setlistService.getSetlist(PERFORMANCE_ID, true)).thenReturn(List.of());

        SetlistResponse response = performanceService.confirmSetlist(OWNER_ID, PERFORMANCE_ID, confirmRequest());

        verify(groupMemberService).ensureOwner(GROUP_ID, OWNER_ID);
        verify(setlistService).confirm(PERFORMANCE_ID, List.of(
                new SetlistService.ConfirmItem(9L, 120L, 1), new SetlistService.ConfirmItem(5L, 88L, 2)));
        assertThat(performance.isSetlistConfirmed()).isTrue();
        assertThat(response.confirmed()).isTrue();
    }

    @Test
    @DisplayName("모임장이 아니면 확정도 확정 해제도 할 수 없고 셋리스트를 건드리지 않는다")
    void confirmAndUnconfirm_throw_whenNotOwner() {
        Performance performance = givenPerformanceForUpdate();
        doThrow(new BusinessException(ErrorCode.NOT_GROUP_OWNER)).when(groupMemberService).ensureOwner(GROUP_ID, MEMBER_ID);

        assertThatThrownBy(() -> performanceService.confirmSetlist(MEMBER_ID, PERFORMANCE_ID, confirmRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_GROUP_OWNER);
        assertThatThrownBy(() -> performanceService.unconfirmSetlist(MEMBER_ID, PERFORMANCE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_GROUP_OWNER);
        verifyNoInteractions(teamMemberService, setlistService);
        assertThat(performance.isSetlistConfirmed()).isFalse();
    }

    @Test
    @DisplayName("끝난 공연(DONE·CANCELED)은 확정할 수 없다(INVALID_STATE)")
    void confirmSetlist_throws_whenFinished() {
        Performance performance = givenPerformanceForUpdate();

        for (PerformanceStatus finished : List.of(PerformanceStatus.DONE, PerformanceStatus.CANCELED)) {
            performance.changeStatus(finished);
            assertThatThrownBy(() -> performanceService.confirmSetlist(OWNER_ID, PERFORMANCE_ID, confirmRequest()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_STATE);
        }
        verifyNoInteractions(teamMemberService, setlistService);
        assertThat(performance.isSetlistConfirmed()).isFalse();
    }

    @Test
    @DisplayName("이 공연 소속이 아닌 팀(독립 팀 포함)이 섞이면 TEAM_NOT_FOUND이고 셋리스트를 건드리지 않는다")
    void confirmSetlist_throws_whenTeamNotInPerformance() {
        givenPerformanceForUpdate();
        when(teamMemberService.getPerformanceId(9L)).thenReturn(null);

        assertThatThrownBy(() -> performanceService.confirmSetlist(OWNER_ID, PERFORMANCE_ID, confirmRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TEAM_NOT_FOUND);
        verifyNoInteractions(setlistService);
    }

    @Test
    @DisplayName("모임장이 확정을 해제하면 순서를 비우게 맡기고 확정 시각을 지운다")
    void unconfirmSetlist_clearsConfirmation() {
        Performance performance = givenPerformanceForUpdate();
        performance.confirmSetlist(LocalDateTime.now());
        when(setlistService.getSetlist(PERFORMANCE_ID, false)).thenReturn(List.of());

        SetlistResponse response = performanceService.unconfirmSetlist(OWNER_ID, PERFORMANCE_ID);

        verify(setlistService).unconfirm(PERFORMANCE_ID);
        assertThat(performance.isSetlistConfirmed()).isFalse();
        assertThat(response.confirmed()).isFalse();
    }

    @Test
    @DisplayName("확정되지 않았거나 끝난 공연이면 확정 해제는 INVALID_STATE다")
    void unconfirmSetlist_throws_whenNotConfirmedOrFinished() {
        Performance performance = givenPerformanceForUpdate();

        assertThatThrownBy(() -> performanceService.unconfirmSetlist(OWNER_ID, PERFORMANCE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_STATE);
        performance.confirmSetlist(LocalDateTime.now());
        performance.changeStatus(PerformanceStatus.DONE);
        assertThatThrownBy(() -> performanceService.unconfirmSetlist(OWNER_ID, PERFORMANCE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_STATE);
        verifyNoInteractions(setlistService);
        assertThat(performance.isSetlistConfirmed()).isTrue();
    }

    private PerformanceCreateRequest createRequest() {
        return new PerformanceCreateRequest("2026 정기공연", "1년에 한 번 하는 정기공연입니다.", PERFORMED_AT,
                "홍대 롤링홀", "poster.jpg");
    }

    private SetlistConfirmRequest confirmRequest() {
        return new SetlistConfirmRequest(List.of(
                new SetlistConfirmRequest.Item(9L, 120L, 1), new SetlistConfirmRequest.Item(5L, 88L, 2)));
    }

    private PerformanceUpdateRequest updateRequest() {
        return new PerformanceUpdateRequest("2026 정기공연 (연기)", null, PERFORMED_AT.plusWeeks(1), "신촌 클럽", null);
    }

    private PerformanceTeamCreateRequest teamCreateRequest() {
        return new PerformanceTeamCreateRequest("무명밴드", "발라드 위주로 갑니다.", Region.SEOUL,
                List.of(Genre.BALLAD, Genre.ROCK), Instrument.BASS);
    }

    private PerformanceStatusUpdateRequest statusRequest(PerformanceStatus status) {
        return new PerformanceStatusUpdateRequest(status);
    }

    private Performance givenPerformance() {
        return givenPerformance(PERFORMED_AT);
    }

    private Performance givenPerformance(LocalDateTime performedAt) {
        Performance performance = Performance.create(GROUP_ID, OWNER_ID, "2026 정기공연", "설명", performedAt,
                "홍대 롤링홀", "poster.jpg");
        when(performanceRepository.findById(PERFORMANCE_ID)).thenReturn(Optional.of(performance));
        return performance;
    }

    // id는 GeneratedValue라 create()로는 못 채운다 — 저장된 공연처럼 리플렉션으로 넣는다
    private Performance performanceWithId(Long id, String title) {
        Performance performance = Performance.create(GROUP_ID, OWNER_ID, title, null, PERFORMED_AT, "홍대 롤링홀", null);
        ReflectionTestUtils.setField(performance, "id", id);
        return performance;
    }

    // 셋리스트를 바꾸는 넘기기·확정·해제는 공연 행 잠금 조회로 공연을 읽는다
    private Performance givenPerformanceForUpdate() {
        Performance performance = Performance.create(GROUP_ID, OWNER_ID, "2026 정기공연", "설명", PERFORMED_AT,
                "홍대 롤링홀", null);
        ReflectionTestUtils.setField(performance, "id", PERFORMANCE_ID);
        when(performanceRepository.findByIdForUpdate(PERFORMANCE_ID)).thenReturn(Optional.of(performance));
        return performance;
    }

    // 상세 응답을 조립할 때 다른 도메인에서 받아오는 조각 — 모임 표시 정보와 만든 사람 요약
    private void givenDetailParts() {
        when(groupMemberService.getGroupBrief(GROUP_ID)).thenReturn(GROUP);
        when(userService.getSummaries(List.of(OWNER_ID))).thenReturn(Map.of(OWNER_ID, CREATOR));
    }
}
