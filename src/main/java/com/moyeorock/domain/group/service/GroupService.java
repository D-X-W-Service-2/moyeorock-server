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
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
     * 내 모임 목록. 전체 모임 목록·검색은 두지 않는다 — 검색({@code keyword}, 모임 이름 부분 일치)도
     * 내 모임 안에서만 한다. 해체된 모임과 내가 탈퇴·강퇴된 모임은 빠진다.
     * 모임 페이지와 그 모임들에서의 내 역할을 따로 조회해 붙이므로 단일 스냅샷으로 묶는다.
     */
    @Transactional(readOnly = true)
    public PageResponse<GroupSummaryResponse> getMyGroups(Long userId, String keyword, Pageable pageable) {
        Page<Group> groups = groupRepository.findMyGroups(userId, normalizeKeyword(keyword), pageable);
        Map<Long, GroupRole> roles = groupMemberService.getActiveRoles(
                userId, groups.map(Group::getId).getContent());
        return PageResponse.from(groups.map(group -> GroupSummaryResponse.of(group, roles.get(group.getId()))));
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
