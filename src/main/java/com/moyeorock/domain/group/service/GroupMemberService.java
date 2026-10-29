package com.moyeorock.domain.group.service;

import com.moyeorock.domain.group.dto.response.GroupMemberResponse;
import com.moyeorock.domain.group.entity.GroupMember;
import com.moyeorock.domain.group.enums.GroupMemberStatus;
import com.moyeorock.domain.group.enums.GroupRole;
import com.moyeorock.domain.group.enums.GroupStatus;
import com.moyeorock.domain.group.repository.GroupMemberRepository;
import com.moyeorock.domain.group.repository.GroupRepository;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모임원·권한 담당. 권한 판별 메서드({@code ensureOwner}·{@code ensureActiveMember})를 공개해
 * notice 등 다른 도메인이 "현재 모임장인가"를 물어볼 수 있게 한다.
 * 모임원의 역할·수·모임장을 돌려주는 조회는 해체된 모임을 빼고 한다(GroupMemberRepository).
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

    /**
     * 모임원 목록. 페이징 count·content 쿼리의 불일치는 화면상 오차로만 남으므로 트랜잭션으로 묶지 않는다.
     * 정렬은 모임장 우선·가입순 고정이라 클라이언트 sort는 버린다(엔티티에 없는 속성이 들어오면 500이 난다).
     */
    public PageResponse<GroupMemberResponse> getMembers(Long userId, Long groupId, Pageable pageable) {
        ensureActiveMember(groupId, userId);
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return PageResponse.from(groupMemberRepository
                .findActiveMembers(groupId, GroupMemberStatus.ACTIVE, unsorted)
                .map(GroupMemberResponse::from));
    }

    /**
     * 역할 변경·모임장 위임. {@code OWNER}로 위임하면 기존 모임장(요청자)을 {@code MEMBER}로 강등한다 —
     * 두 변경이 반만 적용되면 모임장이 0명 또는 2명이 되므로 한 트랜잭션으로 묶는다.
     */
    @Transactional
    public GroupMemberResponse changeRole(Long userId, Long groupId, Long targetUserId, GroupRole role) {
        GroupMember owner = getOwnerOrThrow(groupId, userId);
        if (userId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.CANNOT_CHANGE_OWN_ROLE);
        }

        GroupMember target = getActiveMemberOrThrow(groupId, targetUserId);
        if (role == GroupRole.OWNER) {
            owner.changeRole(GroupRole.MEMBER);
        }
        target.changeRole(role);
        return GroupMemberResponse.from(target);
    }

    /**
     * 내보내기·탈퇴. 요청 바디가 없고 서버가 상태를 정한다 —
     * 요청자 == 대상이면 {@code LEFT}(본인 탈퇴), 모임장이 타인에게 하면 {@code BANNED}(강퇴).
     * 모임장 본인은 위임 전에는 탈퇴할 수 없다.
     *
     * <p>{@link #changeRole}과 같이 <b>권한 확인을 대상 조회보다 먼저</b> 한다 — 권한 없는 요청자에게
     * 다른 모임원의 존재 여부를 404/403으로 구분해 알려주지 않으려는 것이다. 요청자 행을 대상 행보다
     * 먼저 읽는 순서도 두 메서드가 같아진다.
     */
    @Transactional
    public void changeStatus(Long userId, Long groupId, Long targetUserId) {
        if (userId.equals(targetUserId)) {
            ensureGroupExists(groupId);
        } else {
            ensureOwner(groupId, userId);  // 모임 존재 확인을 포함한다 — 중복 조회하지 않는다
        }

        GroupMember target = getActiveMemberOrThrow(groupId, targetUserId);
        if (userId.equals(targetUserId)) {
            if (target.isOwner()) {
                throw new BusinessException(ErrorCode.OWNER_CANNOT_LEAVE);
            }
            target.leave();
            return;
        }

        target.ban();
    }

    @Transactional
    public void addOwner(Long groupId, Long userId) {
        groupMemberRepository.save(GroupMember.create(groupId, userId, GroupRole.OWNER));
    }

    public void ensureOwner(Long groupId, Long userId) {
        getOwnerOrThrow(groupId, userId);
    }

    public void ensureActiveMember(Long groupId, Long userId) {
        ensureGroupExists(groupId);
        if (!groupMemberRepository.existsByGroupIdAndUserIdAndStatus(groupId, userId, GroupMemberStatus.ACTIVE)) {
            throw new BusinessException(ErrorCode.NOT_GROUP_MEMBER);
        }
    }

    /**
     * 해체된 모임도 없는 모임으로 본다 — ensureOwner·ensureActiveMember가 이걸 거치므로
     * 모임원·공지 엔드포인트가 모두 해체된 모임을 404로 막는다. 모임원과 상관없이 모임만 확인할 때
     * (공지 미리보기 등) 다른 도메인도 부른다.
     */
    public void ensureGroupExists(Long groupId) {
        if (!groupRepository.existsByIdAndStatus(groupId, GroupStatus.ACTIVE)) {
            throw new BusinessException(ErrorCode.GROUP_NOT_FOUND);
        }
    }

    /** 모임원이 아니면(해체된 모임 포함) {@code NOT_GROUP_MEMBER}. 모임 상세의 {@code myRole}에 쓴다. */
    public GroupRole getActiveRole(Long groupId, Long userId) {
        return groupMemberRepository.findActiveMember(groupId, userId)
                .map(GroupMember::getRole)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_GROUP_MEMBER));
    }

    /** 해체된 모임이면 0이다. */
    public int countActiveMembers(Long groupId) {
        return groupMemberRepository.countActiveMembers(groupId);
    }

    /** 해체됐거나 없는 모임이면 {@code GROUP_NOT_FOUND} — 해체되지 않은 모임에는 모임장이 항상 한 명 있다. */
    public Long getOwnerId(Long groupId) {
        return groupMemberRepository.findActiveOwner(groupId)
                .map(GroupMember::getUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }

    // 요청자가 이 모임의 현재 모임장인지 확인하고 그 행을 돌려준다 — 위임할 때 다시 조회하지 않고 바로 강등한다.
    private GroupMember getOwnerOrThrow(Long groupId, Long userId) {
        ensureGroupExists(groupId);
        return groupMemberRepository.findActiveMember(groupId, userId)
                .filter(GroupMember::isOwner)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_GROUP_OWNER));
    }

    private GroupMember getActiveMemberOrThrow(Long groupId, Long userId) {
        return groupMemberRepository.findActiveMember(groupId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_MEMBER_NOT_FOUND));
    }
}
