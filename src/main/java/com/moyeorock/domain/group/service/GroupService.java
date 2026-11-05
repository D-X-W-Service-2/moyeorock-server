package com.moyeorock.domain.group.service;

import com.moyeorock.domain.group.dto.request.GroupCreateRequest;
import com.moyeorock.domain.group.dto.request.GroupUpdateRequest;
import com.moyeorock.domain.group.dto.response.GroupDetailResponse;
import com.moyeorock.domain.group.dto.response.GroupSummaryResponse;
import com.moyeorock.domain.group.entity.Group;
import com.moyeorock.domain.group.enums.GroupRole;
import com.moyeorock.domain.group.enums.GroupStatus;
import com.moyeorock.domain.group.repository.GroupRepository;
import com.moyeorock.domain.notice.service.NoticeService;
import com.moyeorock.domain.performance.service.PerformanceService;
import com.moyeorock.domain.recruit.service.RecruitPostService;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.common.enums.NotificationTargetType;
import com.moyeorock.global.common.enums.NotificationType;
import com.moyeorock.global.event.NotificationEvent;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupService {

    // 강퇴 대상이 공연 팀 팀장일 때의 409 문구. 기본 문구("팀장을 위임한 뒤 탈퇴할 수 있습니다.")는 본인 탈퇴용이다.
    private static final String KICK_TEAM_LEADER_MESSAGE = "공연 팀의 팀장인 모임원은 팀장을 위임한 뒤에 내보낼 수 있습니다.";

    private final GroupRepository groupRepository;
    private final GroupMemberService groupMemberService;
    private final NoticeService noticeService;
    private final RecruitPostService recruitPostService;
    private final PerformanceService performanceService;
    private final TeamService teamService;
    private final ApplicationEventPublisher eventPublisher;

    /** 모임 저장과 모임장 등록은 한 단위다 — 하나만 들어가면 주인 없는 모임이 생긴다. 새 모임이라 고정 공지·공연은 없다. */
    @Transactional
    public GroupDetailResponse create(Long userId, GroupCreateRequest request) {
        Group group = groupRepository.save(Group.create(
                request.name(), request.description(), request.type(), request.region(), request.coverImage()));
        groupMemberService.addOwner(group.getId(), userId);
        return GroupDetailResponse.of(group, userId, 1, GroupRole.OWNER, List.of(), List.of());
    }

    /**
     * 내 모임 목록. 전체 모임 목록·검색은 두지 않는다 — 검색({@code keyword}, 모임 이름 부분 일치)도
     * 내 모임 안에서만 한다. 해체된 모임과 내가 탈퇴·강퇴된 모임은 빠진다.
     * 정렬은 가입 최신순 고정이라 클라이언트 sort는 버린다(엔티티에 없는 속성이 들어오면 500이 난다).
     * 모임과 내 역할을 한 쿼리로 받으므로 count·content 불일치만 남아 트랜잭션으로 묶지 않는다.
     */
    public PageResponse<GroupSummaryResponse> getMyGroups(Long userId, String keyword, Pageable pageable) {
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return PageResponse.from(groupRepository.findMyGroups(userId, normalizeKeyword(keyword), unsorted)
                .map(row -> GroupSummaryResponse.of((Group) row[0], (GroupRole) row[1])));
    }

    /**
     * 동아리 홈. 모임·모임원 수·내 역할·고정 공지·다가오는 공연을 각각 조회해 한 화면으로 합치므로
     * 중간에 탈퇴가 커밋되면 {@code memberCount}와 목록이 어긋난다 — 단일 스냅샷이 필요하다.
     */
    @Transactional(readOnly = true)
    public GroupDetailResponse getDetail(Long userId, Long groupId) {
        Group group = getGroupOrThrow(groupId);
        GroupRole myRole = groupMemberService.getActiveRole(groupId, userId);
        return toDetail(group, groupMemberService.getOwnerId(groupId), myRole);
    }

    @Transactional
    public GroupDetailResponse update(Long userId, Long groupId, GroupUpdateRequest request) {
        Group group = getGroupOrThrow(groupId);
        groupMemberService.ensureOwner(groupId, userId);
        group.update(request.name(), request.description(), request.type(), request.region(), request.coverImage());
        // ensureOwner를 통과했으니 요청자가 곧 모임장이다 — 모임장을 다시 조회하지 않는다
        return toDetail(group, userId, GroupRole.OWNER);
    }

    /**
     * 모임 해체(soft delete). 모임원·공지는 그대로 둔다 — 해체된 모임은 조회·권한 확인에서
     * 없는 모임으로 걸러지므로(getGroupOrThrow·GroupMemberService.ensureGroupExists) 따로 정리할 필요가 없다.
     * 모임 밖(recruit)에 노출되는 모집 중 공고는 걸러 줄 곳이 없어 같은 트랜잭션에서 마감한다.
     * 다른 모임원이 남아 있어도 모임장이면 해체할 수 있다.
     * 위임과 동시에 오면 전 모임장이 해체할 수 있으므로 모임원 변경과 같은 모임 행 잠금으로 직렬화한다.
     */
    @Transactional
    public void disband(Long userId, Long groupId) {
        Group group = groupRepository.findByIdAndStatusForUpdate(groupId, GroupStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
        groupMemberService.ensureOwner(groupId, userId);
        group.disband();
        recruitPostService.closeOpenPostsOfGroup(groupId);
    }

    /**
     * 모임원 내보내기·탈퇴. 모임원 상태 변경({@link GroupMemberService#changeStatus} — 본인이면 {@code LEFT}, 모임장이
     * 타인에게 하면 {@code BANNED})과 그 모임 공연 팀에서 빼기({@code TeamService.leavePerformanceTeams})를 한 트랜잭션으로
     * 묶는다. GroupMemberService가 TeamService를 부르면 {@code TeamService → PerformanceMemberService → GroupMemberService}로
     * 순환하므로 여기서 묶는다(domains.md 팀 간 의존).
     *
     * <p>모임원 상태를 먼저 바꾼다 — changeStatus의 모임 행 잠금이 트랜잭션의 첫 조회여야 하고, 권한 없는 요청이 팀 쪽
     * 오류를 먼저 보지 않게 하려는 것이다. 잠그는 순서(모임 → 팀)는 회원 탈퇴({@code leaveAllGroups → leaveAllTeams})와 같다.
     * 대상이 공연 팀 팀장이면 {@code LEADER_MUST_DELEGATE_FIRST}로 모임원 상태 변경까지 롤백된다.
     * 강퇴면 대상에게 알림을 보낸다 — 트랜잭션 안에서 발행해 롤백되면 알림도 남지 않는다. 본인 탈퇴는 알리지 않는다.
     */
    @Transactional
    public void changeMemberStatus(Long userId, Long groupId, Long targetUserId) {
        groupMemberService.changeStatus(userId, groupId, targetUserId);
        boolean kicked = !userId.equals(targetUserId);
        leavePerformanceTeams(targetUserId, groupId, kicked);
        if (kicked) {
            String groupName = getGroupOrThrow(groupId).getName();
            eventPublisher.publishEvent(new NotificationEvent(targetUserId, NotificationType.GROUP_MEMBER_REMOVED,
                    groupName + "에서 내보내졌습니다.", NotificationTargetType.GROUP, groupId));
        }
    }

    // 에러 코드는 그대로 두고 강퇴일 때만 문구를 모임장 입장으로 바꾼다.
    private void leavePerformanceTeams(Long targetUserId, Long groupId, boolean kicked) {
        try {
            teamService.leavePerformanceTeams(targetUserId, performanceService.findIdsByGroupId(groupId));
        } catch (BusinessException e) {
            if (kicked && e.getErrorCode() == ErrorCode.LEADER_MUST_DELEGATE_FIRST) {
                throw new BusinessException(ErrorCode.LEADER_MUST_DELEGATE_FIRST, KICK_TEAM_LEADER_MESSAGE);
            }
            throw e;
        }
    }

    private GroupDetailResponse toDetail(Group group, Long ownerId, GroupRole myRole) {
        Long groupId = group.getId();
        return GroupDetailResponse.of(
                group,
                ownerId,
                groupMemberService.countActiveMembers(groupId),
                myRole,
                noticeService.getPinnedPreview(groupId),
                performanceService.findUpcoming(groupId));
    }

    // 앞뒤 공백은 검색어가 아니다. 비면 필터 없이 내 모임 전체.
    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.strip();
    }

    private Group getGroupOrThrow(Long groupId) {
        return groupRepository.findByIdAndStatus(groupId, GroupStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }
}
