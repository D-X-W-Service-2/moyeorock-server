package com.moyeorock.domain.group.service;

import com.moyeorock.domain.group.dto.request.GroupCreateRequest;
import com.moyeorock.domain.group.dto.request.GroupUpdateRequest;
import com.moyeorock.domain.group.dto.response.GroupDetailResponse;
import com.moyeorock.domain.group.entity.Group;
import com.moyeorock.domain.group.enums.GroupRole;
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
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }
}
