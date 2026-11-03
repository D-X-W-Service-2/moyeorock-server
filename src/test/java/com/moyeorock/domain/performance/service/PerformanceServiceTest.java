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
import com.moyeorock.domain.performance.dto.request.PerformanceUpdateRequest;
import com.moyeorock.domain.performance.dto.response.PerformanceActivityResponse;
import com.moyeorock.domain.performance.dto.response.PerformanceDetailResponse;
import com.moyeorock.domain.performance.entity.Performance;
import com.moyeorock.domain.performance.enums.PerformancePeriod;
import com.moyeorock.domain.performance.enums.PerformanceStatus;
import com.moyeorock.domain.performance.repository.PerformanceRepository;
import com.moyeorock.domain.team.dto.response.PerformanceTeamResponse;
import com.moyeorock.domain.team.enums.TeamStatus;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import com.moyeorock.domain.user.service.UserService;
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
    @DisplayName("일시를 그대로 두면 이미 지난 공연도 고칠 수 있다")
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

    private PerformanceCreateRequest createRequest() {
        return new PerformanceCreateRequest("2026 정기공연", "1년에 한 번 하는 정기공연입니다.", PERFORMED_AT,
                "홍대 롤링홀", "poster.jpg");
    }

    private PerformanceUpdateRequest updateRequest() {
        return new PerformanceUpdateRequest("2026 정기공연 (연기)", null, PERFORMED_AT.plusWeeks(1), "신촌 클럽", null);
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

    // 상세 응답을 조립할 때 다른 도메인에서 받아오는 조각 — 모임 표시 정보와 만든 사람 요약
    private void givenDetailParts() {
        when(groupMemberService.getGroupBrief(GROUP_ID)).thenReturn(GROUP);
        when(userService.getSummaries(List.of(OWNER_ID))).thenReturn(Map.of(OWNER_ID, CREATOR));
    }
}
