package com.moyeorock.domain.performance.service;

import com.moyeorock.domain.group.service.GroupMemberService;
import com.moyeorock.domain.performance.dto.request.PerformanceCreateRequest;
import com.moyeorock.domain.performance.dto.request.PerformanceUpdateRequest;
import com.moyeorock.domain.performance.dto.request.SetlistConfirmRequest;
import com.moyeorock.domain.performance.dto.request.SetlistSubmitRequest;
import com.moyeorock.domain.performance.dto.response.PerformanceDetailResponse;
import com.moyeorock.domain.performance.dto.response.PerformanceSummaryResponse;
import com.moyeorock.domain.performance.dto.response.SetlistResponse;
import com.moyeorock.domain.performance.entity.Performance;
import com.moyeorock.domain.performance.enums.PerformancePeriod;
import com.moyeorock.domain.performance.repository.PerformanceRepository;
import com.moyeorock.domain.setlist.service.SetlistService;
import com.moyeorock.domain.team.service.TeamMemberService;
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
 *
 * <p>공연 셋리스트 엔드포인트도 여기서 받는다(domains.md "헷갈리는 경계") — 확정 시각이 공연 컬럼이라 공연·팀·확정 여부는
 * 여기서 확인하고, 행 처리는 {@link SetlistService}에 맡긴다. 팀 확인은 다른 Service를 부르지 않는 {@link TeamMemberService}로 한다.
 */
@Service
@RequiredArgsConstructor
public class PerformanceService {

    private final PerformanceRepository performanceRepository;
    private final GroupMemberService groupMemberService;
    private final UserService userService;
    private final SetlistService setlistService;
    private final TeamMemberService teamMemberService;

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

    /**
     * 공연 셋리스트 조회(모임원). 확정 전에는 넘겨진 곡 전체, 확정 후에는 확정곡만 순서대로 준다. 끝난 공연도 셋리스트는 그대로다.
     * 공연을 읽어야 모임을 알 수 있어 404가 403보다 먼저다. 다른 도메인 Service 호출이 섞인 조회라 트랜잭션을 걸지 않는다.
     */
    public SetlistResponse getSetlist(Long userId, Long performanceId) {
        Performance performance = getPerformanceOrThrow(performanceId);
        groupMemberService.ensureActiveMember(performance.getGroupId(), userId);
        return SetlistResponse.of(performance, setlistService.getSetlist(performanceId, performance.isSetlistConfirmed()));
    }

    /**
     * 셋리스트로 넘기기(공연 팀 팀장). 그 팀이 이 공연에 넘긴 곡 전체를 바꾼다. 팀장이 아니면 NOT_TEAM_LEADER, 해체·없는 팀이거나
     * 이 공연 소속이 아니면 TEAM_NOT_FOUND, 확정된 셋리스트면 INVALID_STATE다(확정 해제 뒤엔 다시 가능).
     * 공연은 강제 증가 낙관적 락으로 읽는다 — 그사이 확정·해제가 먼저 커밋되면 커밋 때 버전이 달라 409가 된다(이슈 #103).
     */
    @Transactional
    public SetlistResponse submitSetlist(Long userId, Long performanceId, Long teamId, SetlistSubmitRequest request) {
        Performance performance = performanceRepository.findByIdForSetlistSubmit(performanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND));
        teamMemberService.ensureLeader(teamId, userId);
        if (!performanceId.equals(teamMemberService.getPerformanceId(teamId))) {
            throw new BusinessException(ErrorCode.TEAM_NOT_FOUND);
        }
        if (performance.isSetlistConfirmed()) {
            throw new BusinessException(ErrorCode.INVALID_STATE);
        }

        setlistService.submit(performanceId, teamId, request.songIds());
        return SetlistResponse.of(performance, setlistService.getSetlist(performanceId, false));
    }

    /**
     * 셋리스트 확정(모임장). 팀들이 넘긴 곡 중 요청한 곡만 순서대로 남기고 나머지는 지운다. 확정된 셋리스트도 다시 확정할 수 있다.
     * 끝난 공연은 INVALID_STATE(끝난 공연의 셋리스트는 기록), 이 공연 소속이 아닌 팀이 섞이면 TEAM_NOT_FOUND다.
     * 공연 행을 고치므로 버전이 올라, 그사이 커밋된 넘기기나 겹친 넘기기 중 늦은 쪽이 409가 된다(이슈 #103).
     */
    @Transactional
    public SetlistResponse confirmSetlist(Long userId, Long performanceId, SetlistConfirmRequest request) {
        Performance performance = getPerformanceOrThrow(performanceId);
        groupMemberService.ensureOwner(performance.getGroupId(), userId);
        if (performance.isFinished()) {
            throw new BusinessException(ErrorCode.INVALID_STATE);
        }
        request.items().stream().map(SetlistConfirmRequest.Item::teamId).distinct().forEach(teamId -> {
            if (!performanceId.equals(teamMemberService.getPerformanceId(teamId))) {
                throw new BusinessException(ErrorCode.TEAM_NOT_FOUND);
            }
        });

        setlistService.confirm(performanceId, request.items().stream()
                .map(item -> new SetlistService.ConfirmItem(item.teamId(), item.songId(), item.sortOrder()))
                .toList());
        performance.confirmSetlist(LocalDateTime.now());
        return SetlistResponse.of(performance, setlistService.getSetlist(performanceId, true));
    }

    /**
     * 셋리스트 확정 해제(모임장). 남아 있던 곡은 그대로 두고 순서·확정 시각만 비워, 팀이 다시 넘길 수 있게 한다.
     * 확정되지 않았거나 끝난 공연이면 INVALID_STATE다.
     */
    @Transactional
    public SetlistResponse unconfirmSetlist(Long userId, Long performanceId) {
        Performance performance = getPerformanceOrThrow(performanceId);
        groupMemberService.ensureOwner(performance.getGroupId(), userId);
        if (performance.isFinished() || !performance.isSetlistConfirmed()) {
            throw new BusinessException(ErrorCode.INVALID_STATE);
        }

        setlistService.unconfirm(performanceId);
        performance.unconfirmSetlist();
        return SetlistResponse.of(performance, setlistService.getSetlist(performanceId, false));
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
