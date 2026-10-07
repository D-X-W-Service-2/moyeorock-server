package com.moyeorock.domain.group.service;

import com.moyeorock.domain.group.dto.response.GroupMemberResponse;
import com.moyeorock.domain.group.entity.GroupMember;
import com.moyeorock.domain.group.enums.GroupMemberStatus;
import com.moyeorock.domain.group.enums.GroupRole;
import com.moyeorock.domain.group.repository.GroupMemberRepository;
import com.moyeorock.domain.group.repository.GroupRepository;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모임원·권한 담당. 권한 판별 메서드({@code ensureOwner}·{@code ensureActiveMember})를 공개해
 * notice 등 다른 도메인이 "현재 모임장인가"를 물어볼 수 있게 한다.
 *
 * <p>모임 자체(생성·상세·수정)는 {@link GroupService}가 담당한다. 이 클래스가
 * {@code NoticeService}를 모르게 두는 것이 중요하다 — {@code GroupService → NoticeService →
 * GroupMemberService} 방향을 지켜 순환 참조를 만들지 않는다.
 */
@Service
@RequiredArgsConstructor
public class GroupMemberService {

    private final GroupMemberRepository groupMemberRepository;
    private final GroupRepository groupRepository;

    /** 모임원 목록. 페이징 count·content 쿼리의 불일치는 화면상 오차로만 남으므로 트랜잭션으로 묶지 않는다. */
    public PageResponse<GroupMemberResponse> getMembers(Long userId, Long groupId, Pageable pageable) {
        ensureActiveMember(groupId, userId);
        return PageResponse.from(groupMemberRepository
                .findActiveMembers(groupId, GroupMemberStatus.ACTIVE, pageable)
                .map(GroupMemberResponse::from));
    }

    /**
     * 역할 변경·모임장 위임. {@code OWNER}로 위임하면 기존 모임장을 {@code MEMBER}로 강등한다 —
     * 두 변경이 반만 적용되면 모임장이 0명 또는 2명이 되므로 한 트랜잭션으로 묶는다.
     */
    @Transactional
    public GroupMemberResponse changeRole(Long userId, Long groupId, Long targetUserId, GroupRole role) {
        ensureOwner(groupId, userId);
        if (userId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.CANNOT_CHANGE_OWN_ROLE);
        }

        GroupMember target = getActiveMemberOrThrow(groupId, targetUserId);
        if (role == GroupRole.OWNER) {
            getOwnerOrThrow(groupId).changeRole(GroupRole.MEMBER);
        }
        target.changeRole(role);
        return GroupMemberResponse.from(target);
    }

    /**
     * 내보내기·탈퇴. 요청 바디가 없고 서버가 상태를 정한다 —
     * 요청자 == 대상이면 {@code LEFT}(본인 탈퇴), 모임장이 타인에게 하면 {@code BANNED}(강퇴).
     * 모임장 본인은 위임 전에는 탈퇴할 수 없다.
     */
    @Transactional
    public void changeStatus(Long userId, Long groupId, Long targetUserId) {
        ensureGroupExists(groupId);
        GroupMember target = getActiveMemberOrThrow(groupId, targetUserId);

        if (userId.equals(targetUserId)) {
            if (target.getRole() == GroupRole.OWNER) {
                throw new BusinessException(ErrorCode.OWNER_CANNOT_LEAVE);
            }
            target.updateStatus(GroupMemberStatus.LEFT);
            return;
        }

        ensureOwner(groupId, userId);
        target.updateStatus(GroupMemberStatus.BANNED);
    }

    @Transactional
    public void addOwner(Long groupId, Long userId) {
        groupMemberRepository.save(GroupMember.create(groupId, userId, GroupRole.OWNER));
    }

    public void ensureOwner(Long groupId, Long userId) {
        ensureGroupExists(groupId);
        GroupMember member = getActiveMemberOrNull(groupId, userId);
        if (member == null || member.getRole() != GroupRole.OWNER) {
            throw new BusinessException(ErrorCode.NOT_GROUP_OWNER);
        }
    }

    public void ensureActiveMember(Long groupId, Long userId) {
        ensureGroupExists(groupId);
        if (!groupMemberRepository.existsByGroupIdAndUserIdAndStatus(groupId, userId, GroupMemberStatus.ACTIVE)) {
            throw new BusinessException(ErrorCode.NOT_GROUP_MEMBER);
        }
    }

    /** 모임원이 아니면 {@code NOT_GROUP_MEMBER}. 모임 상세의 {@code myRole}에 쓴다. */
    public GroupRole getActiveRole(Long groupId, Long userId) {
        GroupMember member = getActiveMemberOrNull(groupId, userId);
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_GROUP_MEMBER);
        }
        return member.getRole();
    }

    public int countActiveMembers(Long groupId) {
        return groupMemberRepository.countByGroupIdAndStatus(groupId, GroupMemberStatus.ACTIVE);
    }

    public Long getOwnerId(Long groupId) {
        return getOwnerOrThrow(groupId).getUserId();
    }

    private GroupMember getOwnerOrThrow(Long groupId) {
        return groupMemberRepository
                .findByGroupIdAndRoleAndStatus(groupId, GroupRole.OWNER, GroupMemberStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_MEMBER_NOT_FOUND));
    }

    private GroupMember getActiveMemberOrThrow(Long groupId, Long userId) {
        GroupMember member = getActiveMemberOrNull(groupId, userId);
        if (member == null) {
            throw new BusinessException(ErrorCode.GROUP_MEMBER_NOT_FOUND);
        }
        return member;
    }

    private GroupMember getActiveMemberOrNull(Long groupId, Long userId) {
        return groupMemberRepository
                .findByGroupIdAndUserIdAndStatus(groupId, userId, GroupMemberStatus.ACTIVE)
                .orElse(null);
    }

    private void ensureGroupExists(Long groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new BusinessException(ErrorCode.GROUP_NOT_FOUND);
        }
    }
}
