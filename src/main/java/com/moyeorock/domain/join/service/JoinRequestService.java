package com.moyeorock.domain.join.service;

import com.moyeorock.domain.group.dto.response.GroupBriefResponse;
import com.moyeorock.domain.group.service.GroupMemberService;
import com.moyeorock.domain.join.dto.request.InvitationCreateRequest;
import com.moyeorock.domain.join.dto.request.JoinRequestCreateRequest;
import com.moyeorock.domain.join.dto.response.InvitationResponse;
import com.moyeorock.domain.join.dto.response.JoinApprovedResponse;
import com.moyeorock.domain.join.dto.response.JoinDecisionResponse;
import com.moyeorock.domain.join.dto.response.JoinRequestResponse;
import com.moyeorock.domain.join.dto.response.JoinResultResponse;
import com.moyeorock.domain.join.dto.response.JoinTargetResponse;
import com.moyeorock.domain.join.entity.JoinRequest;
import com.moyeorock.domain.join.enums.InvitationDecision;
import com.moyeorock.domain.join.enums.JoinDirection;
import com.moyeorock.domain.join.repository.JoinRequestRepository;
import com.moyeorock.domain.performance.service.PerformanceMemberService;
import com.moyeorock.domain.recruit.dto.response.RecruitPostApplyInfoResponse;
import com.moyeorock.domain.recruit.service.RecruitPostService;
import com.moyeorock.domain.team.dto.response.TeamSummaryResponse;
import com.moyeorock.domain.team.service.TeamMemberService;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import com.moyeorock.domain.user.service.UserService;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.JoinStatus;
import com.moyeorock.global.common.enums.NotificationTargetType;
import com.moyeorock.global.common.enums.NotificationType;
import com.moyeorock.global.common.enums.TargetType;
import com.moyeorock.global.event.NotificationEvent;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입 신청({@code APPLY})과 초대({@code INVITE})를 모두 처리한다. 둘은 엔티티·테이블이 하나이고 승인·수락 때 하는 일
 * (소속 추가·상태 전이·알림)이 같아서 한 Service로 둔다(domains.md "헷갈리는 경계").
 *
 * <p>상태 전이는 신청·초대가 같은 규칙이다 — 대기(PENDING)인 건만 바꿀 수 있고, 누가 하느냐만 다르다.
 * <pre>
 *            승인·수락(APPROVED)   거절(REJECTED)   취소(CANCELED)
 * 신청(APPLY)   팀장 / 모임장        팀장 / 모임장      신청자 본인
 * 초대(INVITE)  초대받은 본인        초대받은 본인      팀장 / 모임장
 * </pre>
 *
 * <p>쓰기 메서드는 모두 트랜잭션 안에서 알림 이벤트를 발행한다(밖에서 발행하면 버려진다).
 */
@Service
@RequiredArgsConstructor
public class JoinRequestService {

    // 명세의 목록에는 정렬 파라미터가 없다 — 최신순 고정.
    private static final Sort LATEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");
    private static final Set<JoinStatus> ALL_STATUSES = EnumSet.allOf(JoinStatus.class);
    private static final String MEMBER_ROLE = "MEMBER";

    private final JoinRequestRepository joinRequestRepository;
    private final UserService userService;
    private final TeamService teamService;
    private final TeamMemberService teamMemberService;
    private final GroupMemberService groupMemberService;
    private final RecruitPostService recruitPostService;
    private final PerformanceMemberService performanceMemberService;
    private final ApplicationEventPublisher eventPublisher;

    // ---------------------------------------------------------------- 가입 신청

    @Transactional
    public JoinRequestResponse apply(Long userId, JoinRequestCreateRequest request) {
        TargetType targetType = request.targetType();
        Long targetId = request.targetId();
        JoinTargetResponse target = getTargetOrThrow(targetType, targetId);
        Instrument instrument = resolveInstrument(targetType, request.instrument());

        // 자기 팀·모임의 팀장/모임장은 신청할 수 없다. 그 밖의 소속원은 ALREADY_MEMBER다.
        if (isManager(targetType, targetId, userId)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        ensureNotMember(targetType, targetId, userId);
        ensurePerformanceGroupMember(targetType, targetId, userId);
        if (request.recruitPostId() != null) {
            validateRecruitPost(request.recruitPostId(), request, instrument, userId);
        }
        ensureNoPending(userId, targetType, targetId);

        JoinRequest saved = joinRequestRepository.save(JoinRequest.apply(userId,
                teamIdOf(targetType, targetId), groupIdOf(targetType, targetId), request.recruitPostId(),
                instrument, request.message()));

        UserSummaryResponse applicant = findUserSummary(userId);
        // 팀 대상은 TEAM_APPLY, 모임 대상은 알맞은 전용 타입이 없어 RECRUIT_POST_APPLIED("지원이 들어왔다")를 쓴다.
        publish(managerIdOf(targetType, targetId),
                targetType == TargetType.TEAM ? NotificationType.TEAM_APPLY : NotificationType.RECRUIT_POST_APPLIED,
                nicknameOf(applicant) + "님이 " + target.name() + " 가입을 신청했습니다.",
                NotificationTargetType.JOIN_REQUEST, saved.getId());
        return JoinRequestResponse.of(saved, target, applicant);
    }

    /** 받은 신청 목록(팀장 / 모임장). 대상을 정해야 권한을 확인할 수 있어 targetType·targetId가 필수다. */
    public PageResponse<JoinRequestResponse> getReceived(Long userId, TargetType targetType, Long targetId,
            JoinStatus status, Pageable pageable) {
        requireTarget(targetType, targetId);
        ensureCanManage(targetType, targetId, userId);
        Page<JoinRequest> page = search(JoinDirection.APPLY, null, targetType, targetId, status, pageable);
        Function<JoinRequest, JoinTargetResponse> targets = targetResolver(page.getContent());
        Map<Long, UserSummaryResponse> users = userService.getSummaries(ids(page, JoinRequest::getUserId));
        return PageResponse.from(page.map(r -> JoinRequestResponse.of(r, targets.apply(r), users.get(r.getUserId()))));
    }

    /** 내 신청 목록. 신청한 팀·모임이 해체됐어도 빼지 않고 target에 id만 남긴다. */
    public PageResponse<JoinRequestResponse> getMine(Long userId, JoinStatus status, Pageable pageable) {
        Page<JoinRequest> page = search(JoinDirection.APPLY, userId, null, null, status, pageable);
        Function<JoinRequest, JoinTargetResponse> targets = targetResolver(page.getContent());
        UserSummaryResponse me = findUserSummary(userId);
        return PageResponse.from(page.map(r -> JoinRequestResponse.of(r, targets.apply(r), me)));
    }

    /** 신청 승인·거절·취소. 결과가 승인이면 {@link JoinApprovedResponse}, 아니면 {@link JoinDecisionResponse}다. */
    @Transactional
    public JoinResultResponse updateStatus(Long userId, Long requestId, JoinStatus outcome) {
        return decide(userId, getForUpdateOrThrow(requestId, JoinDirection.APPLY), outcome);
    }

    // ---------------------------------------------------------------- 초대

    @Transactional
    public InvitationResponse invite(Long userId, InvitationCreateRequest request) {
        TargetType targetType = request.targetType();
        Long targetId = request.targetId();
        Long inviteeId = request.inviteeId();
        if (userId.equals(inviteeId)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        ensureCanManage(targetType, targetId, userId);
        JoinTargetResponse target = getTargetOrThrow(targetType, targetId);
        Instrument instrument = resolveInstrument(targetType, request.instrument());

        Map<Long, UserSummaryResponse> users = userService.getSummaries(List.of(userId, inviteeId));
        UserSummaryResponse invitee = users.get(inviteeId);
        if (invitee == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        ensureNotMember(targetType, targetId, inviteeId);
        ensurePerformanceGroupMember(targetType, targetId, inviteeId);
        ensureNoPending(inviteeId, targetType, targetId);

        JoinRequest saved = joinRequestRepository.save(JoinRequest.invite(userId, inviteeId,
                teamIdOf(targetType, targetId), groupIdOf(targetType, targetId), instrument, request.message()));

        UserSummaryResponse inviter = users.get(userId);
        publish(inviteeId,
                targetType == TargetType.TEAM ? NotificationType.TEAM_INVITE : NotificationType.GROUP_INVITE,
                nicknameOf(inviter) + "님이 " + target.name() + "에 초대했습니다.",
                NotificationTargetType.INVITATION, saved.getId());
        return InvitationResponse.of(saved, target, inviter, invitee);
    }

    /** 보낸 초대 목록(팀장 / 모임장). 가입 신청의 받은 목록과 같은 이유로 targetType·targetId가 필수다. */
    public PageResponse<InvitationResponse> getSent(Long userId, TargetType targetType, Long targetId,
            JoinStatus status, Pageable pageable) {
        requireTarget(targetType, targetId);
        ensureCanManage(targetType, targetId, userId);
        Page<JoinRequest> page = search(JoinDirection.INVITE, null, targetType, targetId, status, pageable);
        return toInvitations(page);
    }

    /** 받은 초대 목록. 내 초대만 보이므로 targetType·targetId는 거르는 용도의 선택 값이다(둘은 함께 줘야 한다). */
    public PageResponse<InvitationResponse> getReceivedInvitations(Long userId, TargetType targetType,
            Long targetId, JoinStatus status, Pageable pageable) {
        if ((targetType == null) != (targetId == null)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        Page<JoinRequest> page = search(JoinDirection.INVITE, userId, targetType, targetId, status, pageable);
        return toInvitations(page);
    }

    /** 초대 수락·거절·취소. 수락은 {@link JoinApprovedResponse}, 그 밖은 {@link JoinDecisionResponse}다. */
    @Transactional
    public JoinResultResponse updateInvitationStatus(Long userId, Long invitationId, InvitationDecision decision) {
        JoinStatus outcome = switch (decision) {
            case ACCEPTED -> JoinStatus.APPROVED;
            case DECLINED -> JoinStatus.REJECTED;
            case CANCELED -> JoinStatus.CANCELED;
        };
        return decide(userId, getForUpdateOrThrow(invitationId, JoinDirection.INVITE), outcome);
    }

    // ---------------------------------------------------------------- 상태 전이 (신청·초대 공통)

    private JoinResultResponse decide(Long userId, JoinRequest request, JoinStatus outcome) {
        if (outcome == JoinStatus.PENDING) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        // 신청은 신청자가 취소하고 나머지는 대상의 팀장/모임장이 한다. 초대는 반대로 받은 사람이 수락·거절하고
        // 팀장/모임장이 취소한다. 권한을 먼저 보고 상태를 본다 — 권한 없는 사람에게 처리 여부를 드러내지 않는다.
        boolean userSide = request.getDirection() == JoinDirection.APPLY
                ? outcome == JoinStatus.CANCELED
                : outcome != JoinStatus.CANCELED;
        if (userSide) {
            if (!request.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.NO_PERMISSION);
            }
        } else {
            ensureCanManage(request.getTargetType(), request.getTargetId(), userId);
        }
        if (!request.isPending()) {
            throw new BusinessException(ErrorCode.INVALID_STATE);
        }

        return switch (outcome) {
            case APPROVED -> approve(userId, request);
            case REJECTED -> {
                request.reject(userId);
                notifyDecision(request, userId, false);
                yield JoinDecisionResponse.from(request);
            }
            case CANCELED -> {
                request.cancel(userId);
                yield JoinDecisionResponse.from(request);
            }
            case PENDING -> throw new IllegalStateException();
        };
    }

    private JoinApprovedResponse approve(Long userId, JoinRequest request) {
        Long joiningUserId = request.getUserId();
        TargetType targetType = request.getTargetType();
        Long targetId = request.getTargetId();
        if (userService.getSummaries(List.of(joiningUserId)).isEmpty()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        // 대기 중에 다른 길로 이미 소속됐다면 승인할 것이 없다. 요청은 대기 상태로 남겨 둔다.
        ensureNotMember(targetType, targetId, joiningUserId);
        if (targetType == TargetType.TEAM) {
            teamService.addMember(targetId, joiningUserId, request.getInstrument());
        } else {
            groupMemberService.addMember(targetId, joiningUserId);
        }
        request.approve(userId);
        notifyDecision(request, userId, true);
        return JoinApprovedResponse.of(request, MEMBER_ROLE);
    }

    // 승인·거절 알림. 신청은 신청자에게, 초대는 초대한 사람에게 간다. 취소는 알리지 않는다(알맞은 타입이 없다).
    private void notifyDecision(JoinRequest request, Long deciderId, boolean approved) {
        String targetName = targetNameOf(request.getTargetType(), request.getTargetId());
        if (request.getDirection() == JoinDirection.APPLY) {
            publish(request.getUserId(),
                    approved ? NotificationType.JOIN_REQUEST_APPROVED : NotificationType.JOIN_REQUEST_REJECTED,
                    targetName + " 가입 신청이 " + (approved ? "승인되었습니다." : "거절되었습니다."),
                    NotificationTargetType.JOIN_REQUEST, request.getId());
            return;
        }
        String nickname = nicknameOf(findUserSummary(deciderId));
        publish(request.getInviterId(),
                approved ? NotificationType.INVITATION_ACCEPTED : NotificationType.INVITATION_DECLINED,
                nickname + "님이 " + targetName + " 초대를 " + (approved ? "수락했습니다." : "거절했습니다."),
                NotificationTargetType.INVITATION, request.getId());
    }

    // 알림 문구용 이름. 해체된 대상의 신청·초대도 거절할 수 있어야 하므로 대상이 없어도 예외 없이 대체 문구를 쓴다.
    private String targetNameOf(TargetType targetType, Long targetId) {
        if (targetType == TargetType.TEAM) {
            TeamSummaryResponse team = teamService.getSummaries(List.of(targetId)).get(targetId);
            return team != null ? team.name() : "해체된 팀";
        }
        return findGroupBrief(targetId).map(GroupBriefResponse::name).orElse("해체된 모임");
    }

    // ---------------------------------------------------------------- 검증

    /** TEAM 대상은 지원 세션이 필수이고, GROUP 대상은 세션이 없다(erd.md §11 — 있어도 저장하지 않는다). */
    private Instrument resolveInstrument(TargetType targetType, Instrument instrument) {
        if (targetType == TargetType.GROUP) {
            return null;
        }
        if (instrument == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return instrument;
    }

    /**
     * 공고를 거친 신청의 검증: 공고가 열려 있고(404·409), 요청 대상과 같은 대상의 공고이고, 내가 쓴 공고가 아니고,
     * 팀 공고라면 모집 중인 세션에 지원했는지(400). 정원이 찼는지는 보지 않는다 — 팀장이 승인 때 판단한다.
     */
    private void validateRecruitPost(Long recruitPostId, JoinRequestCreateRequest request, Instrument instrument,
            Long userId) {
        RecruitPostApplyInfoResponse post = recruitPostService.getOpenPostForApply(recruitPostId);
        boolean sameTarget = post.targetType() == request.targetType() && post.targetId().equals(request.targetId());
        boolean ownPost = post.authorId().equals(userId);
        boolean unwantedInstrument = request.targetType() == TargetType.TEAM
                && !post.wantedInstruments().contains(instrument);
        if (!sameTarget || ownPost || unwantedInstrument) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }

    /** 신청과 초대를 가리지 않고 같은 사람·같은 대상에 대기 중인 건이 있으면 막는다(domains.md). */
    private void ensureNoPending(Long userId, TargetType targetType, Long targetId) {
        boolean exists = targetType == TargetType.TEAM
                ? joinRequestRepository.existsByUserIdAndTargetTeamIdAndStatus(userId, targetId, JoinStatus.PENDING)
                : joinRequestRepository.existsByUserIdAndTargetGroupIdAndStatus(userId, targetId, JoinStatus.PENDING);
        if (exists) {
            throw new BusinessException(ErrorCode.JOIN_REQUEST_DUPLICATED);
        }
    }

    private void ensureNotMember(TargetType targetType, Long targetId, Long userId) {
        boolean member = targetType == TargetType.TEAM
                ? teamMemberService.isActiveMember(targetId, userId)
                : groupMemberService.isActiveMember(targetId, userId);
        if (member) {
            throw new BusinessException(ErrorCode.ALREADY_MEMBER);
        }
    }

    /**
     * 공연 팀(공연에 속한 팀)에는 그 공연을 연 모임의 활동 모임원만 들어올 수 있다(#96). 신청·초대를 만들 때 미리 막아
     * 모임 밖 사람의 신청이 팀장에게 쌓이지 않게 한다. 승인·수락 때는 {@code TeamService.addMember}가 같은 확인을 다시 한다 —
     * 그사이 모임을 나갔을 수 있어서다. 모임 대상이거나 독립 팀이면 확인할 것이 없다.
     */
    private void ensurePerformanceGroupMember(TargetType targetType, Long targetId, Long userId) {
        if (targetType != TargetType.TEAM) {
            return;
        }
        Long performanceId = teamMemberService.getPerformanceId(targetId);
        if (performanceId != null) {
            performanceMemberService.ensureGroupMember(performanceId, userId);
        }
    }

    /** 팀이면 팀장(ensureLeader), 모임이면 모임장(ensureOwner). 없거나 해체된 대상은 404다. */
    private void ensureCanManage(TargetType targetType, Long targetId, Long userId) {
        if (targetType == TargetType.TEAM) {
            teamMemberService.ensureLeader(targetId, userId);
        } else {
            groupMemberService.ensureOwner(targetId, userId);
        }
    }

    private boolean isManager(TargetType targetType, Long targetId, Long userId) {
        return targetType == TargetType.TEAM
                ? teamMemberService.isLeader(targetId, userId)
                : groupMemberService.isOwner(targetId, userId);
    }

    private Long managerIdOf(TargetType targetType, Long targetId) {
        return targetType == TargetType.TEAM
                ? teamMemberService.getLeaderId(targetId)
                : groupMemberService.getOwnerId(targetId);
    }

    private void requireTarget(TargetType targetType, Long targetId) {
        if (targetType == null || targetId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }

    // ---------------------------------------------------------------- 조회·변환

    private JoinRequest getForUpdateOrThrow(Long id, JoinDirection direction) {
        // 신청 id를 초대 경로로 부르거나 그 반대면 "없는 건"으로 본다.
        return joinRequestRepository.findByIdForUpdate(id)
                .filter(r -> r.getDirection() == direction)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOIN_REQUEST_NOT_FOUND));
    }

    private Page<JoinRequest> search(JoinDirection direction, Long userId, TargetType targetType, Long targetId,
            JoinStatus status, Pageable pageable) {
        Pageable latestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), LATEST_FIRST);
        Set<JoinStatus> statuses = status == null ? ALL_STATUSES : EnumSet.of(status);
        Long teamId = targetType == TargetType.TEAM ? targetId : null;
        Long groupId = targetType == TargetType.GROUP ? targetId : null;
        return joinRequestRepository.search(direction, userId, teamId, groupId, statuses, latestFirst);
    }

    private PageResponse<InvitationResponse> toInvitations(Page<JoinRequest> page) {
        Function<JoinRequest, JoinTargetResponse> targets = targetResolver(page.getContent());
        Set<Long> userIds = new LinkedHashSet<>();
        page.getContent().forEach(r -> {
            userIds.add(r.getUserId());
            userIds.add(r.getInviterId());
        });
        Map<Long, UserSummaryResponse> users = userService.getSummaries(userIds);
        return PageResponse.from(page.map(r ->
                InvitationResponse.of(r, targets.apply(r), users.get(r.getInviterId()), users.get(r.getUserId()))));
    }

    /** 없거나 해체된 대상은 404다(TEAM_NOT_FOUND·GROUP_NOT_FOUND). */
    private JoinTargetResponse getTargetOrThrow(TargetType targetType, Long targetId) {
        if (targetType == TargetType.TEAM) {
            TeamSummaryResponse team = teamService.getSummaries(List.of(targetId)).get(targetId);
            if (team == null) {
                throw new BusinessException(ErrorCode.TEAM_NOT_FOUND);
            }
            return JoinTargetResponse.ofTeam(team);
        }
        return JoinTargetResponse.ofGroup(groupMemberService.getGroupBrief(targetId));
    }

    /**
     * 목록의 대상 요약을 한 번에 만든다 — 팀은 일괄 조회 한 번, 모임은 일괄 조회가 없어 중복을 뺀 id마다 단건 조회한다
     * (한 페이지 최대 20건, 4팀 일괄 메서드가 생기면 교체). 해체되거나 없는 대상은 id만 남긴다.
     */
    private Function<JoinRequest, JoinTargetResponse> targetResolver(Collection<JoinRequest> requests) {
        Set<Long> teamIds = new LinkedHashSet<>();
        Set<Long> groupIds = new LinkedHashSet<>();
        for (JoinRequest r : requests) {
            if (r.getTargetType() == TargetType.TEAM) {
                teamIds.add(r.getTargetId());
            } else {
                groupIds.add(r.getTargetId());
            }
        }
        Map<Long, TeamSummaryResponse> teams = teamService.getSummaries(teamIds);
        Map<Long, GroupBriefResponse> groups = new HashMap<>();
        for (Long groupId : groupIds) {
            findGroupBrief(groupId).ifPresent(brief -> groups.put(groupId, brief));
        }
        return r -> {
            Long id = r.getTargetId();
            if (r.getTargetType() == TargetType.TEAM) {
                return Optional.ofNullable(teams.get(id)).map(JoinTargetResponse::ofTeam)
                        .orElseGet(() -> JoinTargetResponse.unresolved(id));
            }
            return Optional.ofNullable(groups.get(id)).map(JoinTargetResponse::ofGroup)
                    .orElseGet(() -> JoinTargetResponse.unresolved(id));
        };
    }

    /** 없거나 해체된 모임은 getGroupBrief가 404를 던지므로 "없음"으로 바꾼다. 그 밖의 오류는 그대로 던진다. */
    private Optional<GroupBriefResponse> findGroupBrief(Long groupId) {
        try {
            return Optional.of(groupMemberService.getGroupBrief(groupId));
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.GROUP_NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    private static Set<Long> ids(Page<JoinRequest> page, Function<JoinRequest, Long> getter) {
        return page.getContent().stream().map(getter).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Long teamIdOf(TargetType targetType, Long targetId) {
        return targetType == TargetType.TEAM ? targetId : null;
    }

    private static Long groupIdOf(TargetType targetType, Long targetId) {
        return targetType == TargetType.GROUP ? targetId : null;
    }

    /** 탈퇴한 사용자는 getSummaries에서 빠져 null이다. */
    private UserSummaryResponse findUserSummary(Long userId) {
        return userService.getSummaries(List.of(userId)).get(userId);
    }

    private static String nicknameOf(UserSummaryResponse user) {
        return user == null ? "알 수 없는 사용자" : user.nickname();
    }

    private void publish(Long receiverId, NotificationType type, String message,
            NotificationTargetType targetType, Long targetId) {
        eventPublisher.publishEvent(new NotificationEvent(receiverId, type, message, targetType, targetId));
    }
}
