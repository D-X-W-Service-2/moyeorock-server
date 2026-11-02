package com.moyeorock.domain.performance.service;

import com.moyeorock.domain.group.service.GroupMemberService;
import com.moyeorock.domain.performance.dto.request.PerformanceCreateRequest;
import com.moyeorock.domain.performance.dto.request.PerformanceUpdateRequest;
import com.moyeorock.domain.performance.dto.response.PerformanceDetailResponse;
import com.moyeorock.domain.performance.dto.response.PerformanceSummaryResponse;
import com.moyeorock.domain.performance.entity.Performance;
import com.moyeorock.domain.performance.enums.PerformancePeriod;
import com.moyeorock.domain.performance.repository.PerformanceRepository;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import com.moyeorock.domain.user.service.UserService;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공연 생성·목록·상세·수정. 모임 권한과 모임 정보는 {@code GroupService}가 아니라 {@code GroupMemberService}로만
 * 받는다 — 나중에 모임 홈의 다가오는 공연 때문에 {@code GroupService → PerformanceService}가 생겨도 순환하지 않게.
 *
 * <p>공연 상태 변경·공연 내 팀 생성과 상세의 {@code teams}·{@code myTeamIds}는 2팀 TeamService(#79)가 생긴 뒤,
 * 상세의 {@code setlist}는 셋리스트 도메인 구현 때 붙인다.
 */
@Service
@RequiredArgsConstructor
public class PerformanceService {

    private final PerformanceRepository performanceRepository;
    private final GroupMemberService groupMemberService;
    private final UserService userService;

    /** 모임장만 만든다. 새 공연은 {@code PLANNED}로 시작하고, 만든 사람이 곧 모임장이라 {@code canEdit}은 true다. */
    @Transactional
    public PerformanceDetailResponse create(Long userId, Long groupId, PerformanceCreateRequest request) {
        groupMemberService.ensureOwner(groupId, userId);
        Performance performance = performanceRepository.save(Performance.create(
                groupId, userId, request.title(), request.description(), request.performedAt(),
                request.venue(), request.posterImage()));
        return toDetail(performance, true);
    }

    /**
     * 모임 공연 목록. {@code period}가 없으면 전체를 공연일 늦은 순으로, {@code UPCOMING}은 가까운 순,
     * {@code PAST}는 최근 순으로 준다. 정렬은 PerformanceRepository 메서드 이름에 고정돼 있어 클라이언트 sort는 버린다 —
     * 엔티티에 없는 속성이 들어오면 500이 난다. 모임원 확인(다른 도메인 Service)이 섞인 조회라 트랜잭션을 걸지 않는다.
     */
    public PageResponse<PerformanceSummaryResponse> getList(Long userId, Long groupId, PerformancePeriod period,
            Pageable pageable) {
        groupMemberService.ensureActiveMember(groupId, userId);
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return PageResponse.from(findPage(groupId, period, unsorted).map(PerformanceSummaryResponse::from));
    }

    /**
     * 공연 상세. 공연을 읽어야 어느 모임인지 알 수 있어 공연 존재(404)를 모임원 확인(403)보다 먼저 한다.
     * 해체된 모임의 공연은 ensureActiveMember가 {@code GROUP_NOT_FOUND}로 막는다.
     * 다른 도메인 Service 호출이 섞인 조회라 트랜잭션을 걸지 않는다(conventions.md §3).
     */
    public PerformanceDetailResponse getDetail(Long userId, Long performanceId) {
        Performance performance = getPerformanceOrThrow(performanceId);
        groupMemberService.ensureActiveMember(performance.getGroupId(), userId);
        return toDetail(performance, groupMemberService.isOwner(performance.getGroupId(), userId));
    }

    /**
     * 모임장만 고친다. ensureOwner를 통과했으니 요청자가 곧 모임장이다 — 역할을 다시 묻지 않는다.
     * 공연 일시를 지난 시각으로 바꾸는 것은 막지만, 일시를 그대로 두면 이미 지난 공연도 고칠 수 있다.
     */
    @Transactional
    public PerformanceDetailResponse update(Long userId, Long performanceId, PerformanceUpdateRequest request) {
        Performance performance = getPerformanceOrThrow(performanceId);
        groupMemberService.ensureOwner(performance.getGroupId(), userId);
        ensureFutureWhenRescheduled(performance, request.performedAt());
        performance.update(request.title(), request.description(), request.performedAt(), request.venue(),
                request.posterImage());
        return toDetail(performance, true);
    }

    private PerformanceDetailResponse toDetail(Performance performance, boolean canEdit) {
        return PerformanceDetailResponse.of(
                performance,
                groupMemberService.getGroupBrief(performance.getGroupId()),
                getCreator(performance),
                canEdit);
    }

    // 만든 사람이 탈퇴했으면 null이다 — getSummaries가 탈퇴 회원을 결과에서 뺀다.
    // created_by는 NULL 허용 컬럼이라 비어 있으면 조회 없이 null이다(List.of는 null 원소에서 NPE를 던진다).
    private UserSummaryResponse getCreator(Performance performance) {
        Long creatorId = performance.getCreatedBy();
        if (creatorId == null) {
            return null;
        }
        return userService.getSummaries(List.of(creatorId)).get(creatorId);
    }

    // 일시가 바뀔 때만 과거인지 본다. DTO에 @Future를 걸면 지난 공연의 설명만 고치는 요청까지 막혀서 여기서 확인한다.
    // 현재 시각은 JVM 기본 타임존 기준이다(Asia/Seoul — global/config/TimezoneConfig, #91).
    private void ensureFutureWhenRescheduled(Performance performance, LocalDateTime performedAt) {
        if (!performedAt.equals(performance.getPerformedAt()) && !performedAt.isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private Page<Performance> findPage(Long groupId, PerformancePeriod period, Pageable unsorted) {
        if (period == null) {
            return performanceRepository.findByGroupIdOrderByPerformedAtDescIdDesc(groupId, unsorted);
        }
        return switch (period) {
            case UPCOMING -> performanceRepository.findByGroupIdAndStatusInOrderByPerformedAtAscIdAsc(
                    groupId, period.getStatuses(), unsorted);
            case PAST -> performanceRepository.findByGroupIdAndStatusInOrderByPerformedAtDescIdDesc(
                    groupId, period.getStatuses(), unsorted);
        };
    }

    private Performance getPerformanceOrThrow(Long performanceId) {
        return performanceRepository.findById(performanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND));
    }
}
