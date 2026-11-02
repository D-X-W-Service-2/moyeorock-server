package com.moyeorock.domain.performance.service;

import com.moyeorock.domain.group.service.GroupMemberService;
import com.moyeorock.domain.performance.dto.request.PerformanceCreateRequest;
import com.moyeorock.domain.performance.dto.request.PerformanceStatusUpdateRequest;
import com.moyeorock.domain.performance.dto.request.PerformanceTeamCreateRequest;
import com.moyeorock.domain.performance.dto.request.PerformanceUpdateRequest;
import com.moyeorock.domain.performance.dto.response.PerformanceDetailResponse;
import com.moyeorock.domain.performance.dto.response.PerformanceStatusResponse;
import com.moyeorock.domain.performance.dto.response.PerformanceSummaryResponse;
import com.moyeorock.domain.performance.entity.Performance;
import com.moyeorock.domain.performance.enums.PerformancePeriod;
import com.moyeorock.domain.performance.repository.PerformanceRepository;
import com.moyeorock.domain.team.dto.response.TeamDetailResponse;
import com.moyeorock.domain.team.service.TeamService;
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
 * 공연 생성·목록·상세·수정·상태 변경과 공연 내 팀 생성. 모임 권한과 모임 정보는 {@code GroupService}가 아니라 {@code GroupMemberService}로만
 * 받는다 — 나중에 모임 홈의 다가오는 공연 때문에 {@code GroupService → PerformanceService}가 생겨도 순환하지 않게.
 *
 * <p>참여 팀({@code teams})과 내가 속한 팀({@code myTeamIds})은 2팀 {@code TeamService}로 받는다.
 * 상세의 {@code setlist}는 셋리스트 도메인 구현 때 붙인다.
 */
@Service
@RequiredArgsConstructor
public class PerformanceService {

    private final PerformanceRepository performanceRepository;
    private final GroupMemberService groupMemberService;
    private final UserService userService;
    private final TeamService teamService;

    /** 모임장만 만든다. 새 공연은 {@code PLANNED}로 시작하고, 만든 사람이 곧 모임장이라 {@code canEdit}은 true다. */
    @Transactional
    public PerformanceDetailResponse create(Long userId, Long groupId, PerformanceCreateRequest request) {
        groupMemberService.ensureOwner(groupId, userId);
        Performance performance = performanceRepository.save(Performance.create(
                groupId, userId, request.title(), request.description(), request.performedAt(),
                request.venue(), request.posterImage()));
        return toDetail(performance, userId, true);
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
        return toDetail(performance, userId, groupMemberService.isOwner(performance.getGroupId(), userId));
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
        return toDetail(performance, userId, true);
    }

    /**
     * 공연 상태 변경. 모임장만 바꾼다. 끝난 공연(종료·취소)은 다시 바꿀 수 없다 — 소속 팀이 이미 해체돼서 되돌려도
     * 팀이 돌아오지 않는다. 종료·취소로 바꾸면 소속 팀을 모두 해체하고, 같은 트랜잭션이라 상태 변경과 함께 커밋·롤백된다.
     */
    @Transactional
    public PerformanceStatusResponse changeStatus(Long userId, Long performanceId,
            PerformanceStatusUpdateRequest request) {
        Performance performance = getPerformanceOrThrow(performanceId);
        groupMemberService.ensureOwner(performance.getGroupId(), userId);
        if (performance.isFinished()) {
            throw new BusinessException(ErrorCode.INVALID_STATE);
        }

        performance.changeStatus(request.status());
        if (performance.isFinished()) {
            teamService.disbandAllByPerformance(performanceId);
        }
        return PerformanceStatusResponse.from(performance);
    }

    /**
     * 공연 내 팀 생성. 모임원이면 만들 수 있고 만든 사람이 팀장이 된다(7.1). 끝난 공연(종료·취소)에는 만들 수 없다.
     * 공연 존재·권한·상태는 여기서 확인하고, 팀 저장은 2팀 TeamService가 한다.
     */
    @Transactional
    public TeamDetailResponse createTeam(Long userId, Long performanceId, PerformanceTeamCreateRequest request) {
        Performance performance = getPerformanceOrThrow(performanceId);
        groupMemberService.ensureActiveMember(performance.getGroupId(), userId);
        if (performance.isFinished()) {
            throw new BusinessException(ErrorCode.INVALID_STATE);
        }

        return teamService.createForPerformance(userId, performanceId, request.name(), request.description(),
                request.region(), request.genres(), request.instrument());
    }

    // 생성·상세·수정이 같은 응답이라 참여 팀도 한 곳에서 채운다 — 막 만든 공연이면 둘 다 빈 목록이다.
    private PerformanceDetailResponse toDetail(Performance performance, Long userId, boolean canEdit) {
        return PerformanceDetailResponse.of(
                performance,
                groupMemberService.getGroupBrief(performance.getGroupId()),
                teamService.getActiveSummaries(performance.getId()),
                getCreator(performance),
                canEdit,
                teamService.findMyTeamIds(performance.getId(), userId));
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
