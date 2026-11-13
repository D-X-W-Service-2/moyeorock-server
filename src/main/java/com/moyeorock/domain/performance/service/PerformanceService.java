package com.moyeorock.domain.performance.service;

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
import com.moyeorock.domain.performance.dto.response.PerformanceSummaryResponse;
import com.moyeorock.domain.performance.dto.response.SetlistResponse;
import com.moyeorock.domain.performance.entity.Performance;
import com.moyeorock.domain.performance.enums.PerformancePeriod;
import com.moyeorock.domain.performance.repository.PerformanceRepository;
import com.moyeorock.domain.setlist.dto.response.SetlistItemResponse;
import com.moyeorock.domain.setlist.service.SetlistService;
import com.moyeorock.domain.team.dto.response.PerformanceTeamResponse;
import com.moyeorock.domain.team.dto.response.TeamDetailResponse;
import com.moyeorock.domain.team.enums.TeamStatus;
import com.moyeorock.domain.team.service.TeamMemberService;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import com.moyeorock.domain.user.service.UserService;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공연 생성·목록·상세·수정·상태 변경과 공연 내 팀 생성. 모임 권한과 모임 정보는 {@code GroupService}가 아니라 {@code GroupMemberService}로만
 * 받는다 — 모임 홈의 다가오는 공연 때문에 {@code GroupService → PerformanceService}가 있어서 순환하지 않게.
 *
 * <p>참여 팀({@code teams})과 내가 속한 팀({@code myTeamIds})은 2팀 {@code TeamService}로 받는다.
 * 상세의 확정 셋리스트({@code setlist})는 {@link SetlistService}로 받는다.
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
    // TeamService는 공연 쪽을 PerformanceMemberService로만 불러서 PerformanceService와 순환하지 않는다
    private final TeamService teamService;
    private final SetlistService setlistService;
    private final TeamMemberService teamMemberService;

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
     * 동아리 홈의 다가오는 공연 — {@code PLANNED}·{@code RECRUITING} 공연을 공연일 가까운 순으로 최대 3건(공연 목록 UPCOMING과
     * 같은 기준). GroupService가 부르고 모임 권한은 부르는 쪽이 확인한다. 없으면 빈 목록이다.
     */
    public List<PerformanceSummaryResponse> findUpcoming(Long groupId) {
        return performanceRepository.findTop3ByGroupIdAndStatusInOrderByPerformedAtAscIdAsc(
                groupId, PerformancePeriod.UPCOMING.getStatuses()).stream()
                .map(PerformanceSummaryResponse::from)
                .toList();
    }

    /**
     * 모임의 공연 id 전체 — GroupService가 모임 탈퇴·강퇴 때 공연 팀에서 빼려고 부른다. 모임 권한은 부르는 쪽이 확인한다.
     * 상태로 거르지 않는다. 끝난 공연의 팀은 이미 해체돼 {@code TeamService.leavePerformanceTeams}가 건너뛰고,
     * 공연 종료와 팀 생성이 겹쳐 끝난 공연에 남은 활동 팀(architecture.md §7)도 함께 정리된다. 없으면 빈 목록이다.
     */
    public List<Long> findIdsByGroupId(Long groupId) {
        return performanceRepository.findIdsByGroupId(groupId);
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
     * 끝난 공연(종료·취소)은 고칠 수 없다(INVALID_STATE) — 끝난 공연은 기록이라 상태·팀·셋리스트와 같이 막는다.
     * 공연 일시를 지난 시각으로 바꾸는 것은 막지만, 일시를 그대로 두면 일시가 지났어도 아직 끝나지 않은 공연은 고칠 수 있다.
     */
    @Transactional
    public PerformanceDetailResponse update(Long userId, Long performanceId, PerformanceUpdateRequest request) {
        Performance performance = getPerformanceOrThrow(performanceId);
        groupMemberService.ensureOwner(performance.getGroupId(), userId);
        if (performance.isFinished()) {
            throw new BusinessException(ErrorCode.INVALID_STATE);
        }
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
     * 공연 행을 잠그고 시작한다 — 확정·해제나 다른 팀의 넘기기와 겹치면 앞 요청이 끝난 뒤의 상태로 판단한다(이슈 #103).
     */
    @Transactional
    public SetlistResponse submitSetlist(Long userId, Long performanceId, Long teamId, SetlistSubmitRequest request) {
        Performance performance = getPerformanceForUpdate(performanceId);
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
     * 넘기기와 같은 공연 행 잠금으로 시작해, 진행 중인 넘기기가 있으면 그 커밋을 기다렸다가 넘겨진 곡을 보고 확정한다(이슈 #103).
     */
    @Transactional
    public SetlistResponse confirmSetlist(Long userId, Long performanceId, SetlistConfirmRequest request) {
        Performance performance = getPerformanceForUpdate(performanceId);
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
     * 확정되지 않았거나 끝난 공연이면 INVALID_STATE다. 넘기기·확정과 같은 공연 행 잠금으로 시작한다.
     */
    @Transactional
    public SetlistResponse unconfirmSetlist(Long userId, Long performanceId) {
        Performance performance = getPerformanceForUpdate(performanceId);
        groupMemberService.ensureOwner(performance.getGroupId(), userId);
        if (performance.isFinished() || !performance.isSetlistConfirmed()) {
            throw new BusinessException(ErrorCode.INVALID_STATE);
        }

        setlistService.unconfirm(performanceId);
        performance.unconfirmSetlist();
        return SetlistResponse.of(performance, setlistService.getSetlist(performanceId, false));
    }

    /**
     * 내 활동의 참여 중인 공연(1팀 내 활동 #90, PR #84 답글 2026-11-02). 내가 ACTIVE 팀원인 해체되지 않은 공연 팀이 나가는
     * {@code PLANNED}·{@code RECRUITING} 공연을 공연일 가까운 순으로 주고, 한 공연에 내 팀이 여럿이면 팀마다 한 줄이다.
     * 해체된 모임의 공연은 getNames가 그 모임을 빼므로 함께 빠진다.
     * 다른 도메인 Service 호출이 섞인 조회라 트랜잭션을 걸지 않는다(conventions.md §3).
     */
    public List<PerformanceActivityResponse> findActivitiesByUserId(Long userId) {
        // findMyPerformanceTeams는 해체된 팀도 담아 주므로(끝난 공연용) 진행 중 공연에서는 활동 중인 팀만 남긴다
        Map<Long, List<PerformanceTeamResponse>> myTeamsByPerformance = teamService.findMyPerformanceTeams(userId)
                .stream()
                .filter(team -> team.status() == TeamStatus.ACTIVE)
                .collect(Collectors.groupingBy(PerformanceTeamResponse::performanceId));
        if (myTeamsByPerformance.isEmpty()) {
            return List.of();
        }
        List<Performance> performances = performanceRepository.findByIdInAndStatusInOrderByPerformedAtAscIdAsc(
                myTeamsByPerformance.keySet(), PerformancePeriod.UPCOMING.getStatuses());
        Map<Long, String> groupNames = groupMemberService.getNames(
                performances.stream().map(Performance::getGroupId).distinct().toList());
        return performances.stream()
                .filter(performance -> groupNames.containsKey(performance.getGroupId()))
                .flatMap(performance -> myTeamsByPerformance.get(performance.getId()).stream()
                        .map(team -> PerformanceActivityResponse.of(
                                performance, groupNames.get(performance.getGroupId()), team.teamName())))
                .toList();
    }

    // 생성·상세·수정이 같은 응답이라 참여 팀·셋리스트도 한 곳에서 채운다 — 막 만든 공연이면 셋 다 빈 목록이다.
    private PerformanceDetailResponse toDetail(Performance performance, Long userId, boolean canEdit) {
        return PerformanceDetailResponse.of(
                performance,
                groupMemberService.getGroupBrief(performance.getGroupId()),
                teamService.getActiveSummaries(performance.getId()),
                getConfirmedSetlist(performance),
                getCreator(performance),
                canEdit,
                teamService.findMyTeamIds(performance.getId(), userId));
    }

    // 확정 전이면 넘겨진 곡이 있어도 담지 않는다(명세 "확정된 것만") — 조회 없이 빈 목록이다.
    private List<SetlistItemResponse> getConfirmedSetlist(Performance performance) {
        if (!performance.isSetlistConfirmed()) {
            return List.of();
        }
        return setlistService.getSetlist(performance.getId(), true);
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

    // 셋리스트를 바꾸는 요청(넘기기·확정·해제)은 공연 행을 잠그고 시작해 같은 공연에서 차례로 처리된다
    private Performance getPerformanceForUpdate(Long performanceId) {
        return performanceRepository.findByIdForUpdate(performanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND));
    }
}
