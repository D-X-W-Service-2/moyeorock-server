package com.moyeorock.domain.group.service;

import com.moyeorock.domain.group.dto.request.GroupCreateRequest;
import com.moyeorock.domain.group.dto.request.GroupUpdateRequest;
import com.moyeorock.domain.group.dto.response.GroupDetailResponse;
import com.moyeorock.domain.group.entity.Group;
import com.moyeorock.domain.group.enums.GroupRole;
import com.moyeorock.domain.group.enums.GroupStatus;
import com.moyeorock.domain.group.repository.GroupRepository;
import com.moyeorock.domain.notice.service.NoticeService;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberService groupMemberService;
    private final NoticeService noticeService;

    /** 모임 저장과 모임장 등록은 한 단위다 — 하나만 들어가면 주인 없는 모임이 생긴다. */
    @Transactional
    public GroupDetailResponse create(Long userId, GroupCreateRequest request) {
        Group group = groupRepository.save(Group.create(
                request.name(), request.description(), request.type(), request.region(), request.coverImage()));
        groupMemberService.addOwner(group.getId(), userId);
        return GroupDetailResponse.of(group, userId, 1, GroupRole.OWNER, noticeService.getPinnedPreview(group.getId()));
    }

    /**
     * 동아리 홈. 모임·모임원 수·내 역할·고정 공지를 각각 조회해 한 화면으로 합치므로
     * 중간에 탈퇴가 커밋되면 {@code memberCount}와 목록이 어긋난다 — 단일 스냅샷이 필요하다.
     */
    @Transactional(readOnly = true)
    public GroupDetailResponse getDetail(Long userId, Long groupId) {
        Group group = getGroupOrThrow(groupId);
        return toDetail(group, groupMemberService.getActiveRole(groupId, userId));
    }

    @Transactional
    public GroupDetailResponse update(Long userId, Long groupId, GroupUpdateRequest request) {
        Group group = getGroupOrThrow(groupId);
        groupMemberService.ensureOwner(groupId, userId);
        group.update(request.name(), request.description(), request.type(), request.region(), request.coverImage());
        return toDetail(group, GroupRole.OWNER);
    }

    /**
     * 모임 해체(soft delete). 모임 상태만 바꾸고 모임원·공지는 그대로 둔다 — 해체된 모임은 조회·권한 확인에서
     * 없는 모임으로 걸러지므로(getGroupOrThrow·GroupMemberService.ensureGroupExists) 따로 정리할 필요가 없다.
     * 다른 모임원이 남아 있어도 모임장이면 해체할 수 있다.
     */
    @Transactional
    public void disband(Long userId, Long groupId) {
        Group group = getGroupOrThrow(groupId);
        groupMemberService.ensureOwner(groupId, userId);
        group.disband();
    }

    private GroupDetailResponse toDetail(Group group, GroupRole myRole) {
        Long groupId = group.getId();
        return GroupDetailResponse.of(
                group,
                groupMemberService.getOwnerId(groupId),
                groupMemberService.countActiveMembers(groupId),
                myRole,
                noticeService.getPinnedPreview(groupId));
    }

    private Group getGroupOrThrow(Long groupId) {
        return groupRepository.findByIdAndStatus(groupId, GroupStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }
}
