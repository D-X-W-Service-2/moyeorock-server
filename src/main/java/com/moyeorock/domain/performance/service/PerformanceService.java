package com.moyeorock.domain.performance.service;

import com.moyeorock.domain.group.service.GroupMemberService;
import com.moyeorock.domain.performance.dto.request.PerformanceCreateRequest;
import com.moyeorock.domain.performance.dto.request.PerformanceUpdateRequest;
import com.moyeorock.domain.performance.dto.response.PerformanceDetailResponse;
import com.moyeorock.domain.performance.dto.response.PerformanceSummaryResponse;
import com.moyeorock.domain.performance.entity.Performance;
import com.moyeorock.domain.performance.repository.PerformanceRepository;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import com.moyeorock.domain.user.service.UserService;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
     * 모임 공연 목록. 정렬은 공연일 늦은 순 고정(PerformanceRepository)이라 클라이언트 sort는 버린다 —
     * 엔티티에 없는 속성이 들어오면 500이 난다. 모임원 확인(다른 도메인 Service)이 섞인 조회라 트랜잭션을 걸지 않는다.
     */
    public PageResponse<PerformanceSummaryResponse> getList(Long userId, Long groupId, Pageable pageable) {
        groupMemberService.ensureActiveMember(groupId, userId);
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return PageResponse.from(performanceRepository
                .findByGroupIdOrderByPerformedAtDescIdDesc(groupId, unsorted)
                .map(PerformanceSummaryResponse::from));
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

    /** 모임장만 고친다. ensureOwner를 통과했으니 요청자가 곧 모임장이다 — 역할을 다시 묻지 않는다. */
    @Transactional
    public PerformanceDetailResponse update(Long userId, Long performanceId, PerformanceUpdateRequest request) {
        Performance performance = getPerformanceOrThrow(performanceId);
        groupMemberService.ensureOwner(performance.getGroupId(), userId);
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
    private UserSummaryResponse getCreator(Performance performance) {
        Long creatorId = performance.getCreatedBy();
        return userService.getSummaries(List.of(creatorId)).get(creatorId);
    }

    private Performance getPerformanceOrThrow(Long performanceId) {
        return performanceRepository.findById(performanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND));
    }
}
