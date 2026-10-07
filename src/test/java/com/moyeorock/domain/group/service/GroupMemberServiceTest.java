package com.moyeorock.domain.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moyeorock.domain.group.entity.GroupMember;
import com.moyeorock.domain.group.enums.GroupMemberStatus;
import com.moyeorock.domain.group.enums.GroupRole;
import com.moyeorock.domain.group.repository.GroupMemberRepository;
import com.moyeorock.domain.group.repository.GroupRepository;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupMemberServiceTest {

    private static final Long GROUP_ID = 1L;
    private static final Long OWNER_ID = 10L;
    private static final Long MEMBER_ID = 20L;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private GroupRepository groupRepository;

    private GroupMemberService groupMemberService;

    @BeforeEach
    void setUp() {
        groupMemberService = new GroupMemberService(groupMemberRepository, groupRepository);
    }

    @Test
    @DisplayName("모임장을 위임하면 대상이 OWNER로 올라가고 기존 모임장은 MEMBER로 내려간다")
    void changeRole_delegatesOwnership_andDemotesPreviousOwner() {
        GroupMember owner = member(OWNER_ID, GroupRole.OWNER);
        GroupMember target = member(MEMBER_ID, GroupRole.MEMBER);
        givenGroupExists();
        givenActiveMember(OWNER_ID, owner);
        givenActiveMember(MEMBER_ID, target);
        when(groupMemberRepository.findByGroupIdAndRoleAndStatus(GROUP_ID, GroupRole.OWNER, GroupMemberStatus.ACTIVE))
                .thenReturn(Optional.of(owner));

        groupMemberService.changeRole(OWNER_ID, GROUP_ID, MEMBER_ID, GroupRole.OWNER);

        assertThat(target.getRole()).isEqualTo(GroupRole.OWNER);
        assertThat(owner.getRole()).isEqualTo(GroupRole.MEMBER);
    }

    @Test
    @DisplayName("MANAGER로 변경할 때는 기존 모임장을 건드리지 않는다")
    void changeRole_toManager_doesNotTouchOwner() {
        GroupMember owner = member(OWNER_ID, GroupRole.OWNER);
        GroupMember target = member(MEMBER_ID, GroupRole.MEMBER);
        givenGroupExists();
        givenActiveMember(OWNER_ID, owner);
        givenActiveMember(MEMBER_ID, target);

        groupMemberService.changeRole(OWNER_ID, GROUP_ID, MEMBER_ID, GroupRole.MANAGER);

        assertThat(target.getRole()).isEqualTo(GroupRole.MANAGER);
        assertThat(owner.getRole()).isEqualTo(GroupRole.OWNER);
        verify(groupMemberRepository, never())
                .findByGroupIdAndRoleAndStatus(GROUP_ID, GroupRole.OWNER, GroupMemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("자기 자신의 역할은 변경할 수 없다")
    void changeRole_throws_whenTargetIsSelf() {
        givenGroupExists();
        givenActiveMember(OWNER_ID, member(OWNER_ID, GroupRole.OWNER));

        assertThatThrownBy(() -> groupMemberService.changeRole(OWNER_ID, GROUP_ID, OWNER_ID, GroupRole.MEMBER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CANNOT_CHANGE_OWN_ROLE);
    }

    @Test
    @DisplayName("모임장이 아니면 역할을 변경할 수 없다")
    void changeRole_throws_whenRequesterIsNotOwner() {
        givenGroupExists();
        givenActiveMember(MEMBER_ID, member(MEMBER_ID, GroupRole.MEMBER));

        assertThatThrownBy(() -> groupMemberService.changeRole(MEMBER_ID, GROUP_ID, OWNER_ID, GroupRole.OWNER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_GROUP_OWNER);
    }

    @Test
    @DisplayName("MANAGER도 역할 변경 권한은 없다 — 지금은 MEMBER와 동일 취급")
    void changeRole_throws_whenRequesterIsManager() {
        givenGroupExists();
        givenActiveMember(MEMBER_ID, member(MEMBER_ID, GroupRole.MANAGER));

        assertThatThrownBy(() -> groupMemberService.changeRole(MEMBER_ID, GROUP_ID, OWNER_ID, GroupRole.MEMBER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_GROUP_OWNER);
    }

    @Test
    @DisplayName("본인이 탈퇴하면 상태가 LEFT가 된다")
    void changeStatus_setsLeft_whenMemberLeavesSelf() {
        GroupMember target = member(MEMBER_ID, GroupRole.MEMBER);
        givenGroupExists();
        givenActiveMember(MEMBER_ID, target);

        groupMemberService.changeStatus(MEMBER_ID, GROUP_ID, MEMBER_ID);

        assertThat(target.getStatus()).isEqualTo(GroupMemberStatus.LEFT);
    }

    @Test
    @DisplayName("모임장이 다른 모임원을 내보내면 상태가 BANNED가 된다")
    void changeStatus_setsBanned_whenOwnerRemovesOther() {
        GroupMember target = member(MEMBER_ID, GroupRole.MEMBER);
        givenGroupExists();
        givenActiveMember(MEMBER_ID, target);
        givenActiveMember(OWNER_ID, member(OWNER_ID, GroupRole.OWNER));

        groupMemberService.changeStatus(OWNER_ID, GROUP_ID, MEMBER_ID);

        assertThat(target.getStatus()).isEqualTo(GroupMemberStatus.BANNED);
    }

    @Test
    @DisplayName("모임장은 위임 없이 탈퇴할 수 없다")
    void changeStatus_throws_whenOwnerLeavesWithoutDelegation() {
        GroupMember owner = member(OWNER_ID, GroupRole.OWNER);
        givenGroupExists();
        givenActiveMember(OWNER_ID, owner);

        assertThatThrownBy(() -> groupMemberService.changeStatus(OWNER_ID, GROUP_ID, OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.OWNER_CANNOT_LEAVE);
        assertThat(owner.getStatus()).isEqualTo(GroupMemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("모임장이 아닌 사람이 남을 내보내려 하면 거부된다")
    void changeStatus_throws_whenNonOwnerRemovesOther() {
        givenGroupExists();
        givenActiveMember(MEMBER_ID, member(MEMBER_ID, GroupRole.MEMBER));
        givenActiveMember(30L, member(30L, GroupRole.MEMBER));

        assertThatThrownBy(() -> groupMemberService.changeStatus(30L, GROUP_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_GROUP_OWNER);
    }

    @Test
    @DisplayName("존재하지 않는 모임이면 GROUP_NOT_FOUND를 던진다")
    void ensureActiveMember_throws_whenGroupMissing() {
        when(groupRepository.existsById(GROUP_ID)).thenReturn(false);

        assertThatThrownBy(() -> groupMemberService.ensureActiveMember(GROUP_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    @DisplayName("모임원이 아니면 NOT_GROUP_MEMBER를 던진다")
    void ensureActiveMember_throws_whenNotMember() {
        givenGroupExists();
        when(groupMemberRepository.existsByGroupIdAndUserIdAndStatus(GROUP_ID, MEMBER_ID, GroupMemberStatus.ACTIVE))
                .thenReturn(false);

        assertThatThrownBy(() -> groupMemberService.ensureActiveMember(GROUP_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
    }

    @Test
    @DisplayName("탈퇴한 모임원을 다시 내보내려 하면 GROUP_MEMBER_NOT_FOUND를 던진다")
    void changeStatus_throws_whenTargetIsNotActive() {
        givenGroupExists();
        when(groupMemberRepository.findByGroupIdAndUserIdAndStatus(GROUP_ID, MEMBER_ID, GroupMemberStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupMemberService.changeStatus(OWNER_ID, GROUP_ID, MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.GROUP_MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("모임 생성 시 등록되는 첫 모임원은 OWNER이고 상태는 ACTIVE다")
    void addOwner_savesOwnerWithActiveStatus() {
        groupMemberService.addOwner(GROUP_ID, OWNER_ID);

        var captor = org.mockito.ArgumentCaptor.forClass(GroupMember.class);
        verify(groupMemberRepository).save(captor.capture());
        GroupMember saved = captor.getValue();
        assertThat(saved.getGroupId()).isEqualTo(GROUP_ID);
        assertThat(saved.getUserId()).isEqualTo(OWNER_ID);
        assertThat(saved.getRole()).isEqualTo(GroupRole.OWNER);
        assertThat(saved.getStatus()).isEqualTo(GroupMemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("권한 확인은 대상 조회보다 먼저 한다 — 권한 없는 요청에 다른 모임원 존재 여부를 노출하지 않는다")
    void changeRole_checksPermissionBeforeLookingUpTarget() {
        givenGroupExists();
        givenActiveMember(OWNER_ID, member(OWNER_ID, GroupRole.OWNER));
        givenActiveMember(MEMBER_ID, member(MEMBER_ID, GroupRole.MEMBER));

        groupMemberService.changeRole(OWNER_ID, GROUP_ID, MEMBER_ID, GroupRole.MANAGER);

        InOrder order = inOrder(groupRepository, groupMemberRepository);
        order.verify(groupRepository).existsById(GROUP_ID);
        order.verify(groupMemberRepository)
                .findByGroupIdAndUserIdAndStatus(GROUP_ID, OWNER_ID, GroupMemberStatus.ACTIVE);
        order.verify(groupMemberRepository)
                .findByGroupIdAndUserIdAndStatus(GROUP_ID, MEMBER_ID, GroupMemberStatus.ACTIVE);
    }

    private void givenGroupExists() {
        when(groupRepository.existsById(GROUP_ID)).thenReturn(true);
    }

    private void givenActiveMember(Long userId, GroupMember member) {
        when(groupMemberRepository.findByGroupIdAndUserIdAndStatus(GROUP_ID, userId, GroupMemberStatus.ACTIVE))
                .thenReturn(Optional.of(member));
    }

    private static GroupMember member(Long userId, GroupRole role) {
        return GroupMember.create(GROUP_ID, userId, role);
    }
}
