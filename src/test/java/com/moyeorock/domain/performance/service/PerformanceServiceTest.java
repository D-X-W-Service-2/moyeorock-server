package com.moyeorock.domain.performance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.moyeorock.domain.performance.dto.response.PerformanceDetailResponse;
import com.moyeorock.domain.performance.entity.Performance;
import com.moyeorock.domain.performance.enums.PerformanceStatus;
import com.moyeorock.domain.performance.repository.PerformanceRepository;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import com.moyeorock.domain.user.service.UserService;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.time.LocalDateTime;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class PerformanceServiceTest {

    private static final Long GROUP_ID = 1L;
    private static final Long OWNER_ID = 10L;
    private static final Long MEMBER_ID = 20L;
    private static final Long PERFORMANCE_ID = 100L;
    private static final LocalDateTime PERFORMED_AT = LocalDateTime.of(2026, 11, 22, 18, 0);
    private static final GroupBriefResponse GROUP = new GroupBriefResponse(GROUP_ID, "홍대 밴드부", "cover.jpg");
    private static final UserSummaryResponse CREATOR = new UserSummaryResponse(OWNER_ID, "모임장", null);

    @Mock
    private PerformanceRepository performanceRepository;

    @Mock
    private GroupMemberService groupMemberService;

    @Mock
    private UserService userService;

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

        assertThatThrownBy(() -> performanceService.getList(MEMBER_ID, GROUP_ID, PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
        verifyNoInteractions(performanceRepository);
    }

    @Test
    @DisplayName("공연 목록은 클라이언트 sort를 버리고 page·size만 넘긴다 — 정렬은 Repository 메서드에 고정돼 있다")
    void getList_dropsClientSort() {
        when(performanceRepository.findByGroupIdOrderByPerformedAtDescIdDesc(any(), any())).thenReturn(Page.empty());

        performanceService.getList(MEMBER_ID, GROUP_ID, PageRequest.of(1, 10, Sort.by("title")));

        verify(performanceRepository).findByGroupIdOrderByPerformedAtDescIdDesc(GROUP_ID, PageRequest.of(1, 10));
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
    @DisplayName("없는 공연을 고치려 하면 PERFORMANCE_NOT_FOUND다")
    void update_throws_whenPerformanceMissing() {
        when(performanceRepository.findById(PERFORMANCE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> performanceService.update(OWNER_ID, PERFORMANCE_ID, updateRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PERFORMANCE_NOT_FOUND);
        verifyNoInteractions(groupMemberService);
    }

    private PerformanceCreateRequest createRequest() {
        return new PerformanceCreateRequest("2026 정기공연", "1년에 한 번 하는 정기공연입니다.", PERFORMED_AT,
                "홍대 롤링홀", "poster.jpg");
    }

    private PerformanceUpdateRequest updateRequest() {
        return new PerformanceUpdateRequest("2026 정기공연 (연기)", null, PERFORMED_AT.plusWeeks(1), "신촌 클럽", null);
    }

    private Performance givenPerformance() {
        Performance performance = Performance.create(GROUP_ID, OWNER_ID, "2026 정기공연", "설명", PERFORMED_AT,
                "홍대 롤링홀", "poster.jpg");
        when(performanceRepository.findById(PERFORMANCE_ID)).thenReturn(Optional.of(performance));
        return performance;
    }

    // 상세 응답을 조립할 때 다른 도메인에서 받아오는 조각 — 모임 표시 정보와 만든 사람 요약
    private void givenDetailParts() {
        when(groupMemberService.getGroupBrief(GROUP_ID)).thenReturn(GROUP);
        when(userService.getSummaries(List.of(OWNER_ID))).thenReturn(Map.of(OWNER_ID, CREATOR));
    }
}
