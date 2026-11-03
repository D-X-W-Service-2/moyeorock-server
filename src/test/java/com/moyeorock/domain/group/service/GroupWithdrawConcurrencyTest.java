package com.moyeorock.domain.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.group.entity.Group;
import com.moyeorock.domain.group.entity.GroupMember;
import com.moyeorock.domain.group.enums.GroupMemberStatus;
import com.moyeorock.domain.group.enums.GroupRole;
import com.moyeorock.domain.group.enums.GroupType;
import com.moyeorock.domain.group.repository.GroupMemberRepository;
import com.moyeorock.domain.group.repository.GroupRepository;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 회원 탈퇴의 모임 처리(leaveAllGroups)가 "탈퇴 흐름이 먼저 읽은 뒤 모임장 위임이 커밋된" 경우를 실제 MySQL에서 잡는지 본다(#112).
 * 1팀은 같은 트랜잭션에서 findOwnedGroups로 먼저 확인하므로 스냅샷이 그때 잡힌다 — 일반 조회로 다시 읽으면 위임을 못 봐서
 * 모임장 행을 LEFT로 바꿔 모임장 없는 모임이 생긴다. 잠금 조회로 다시 읽는지가 이 테스트의 관심사다.
 *
 * <p>위임이 따로 커밋되어야 하므로 클래스에 {@code @Transactional}을 붙이지 않고 남은 행을 직접 지운다
 * (PerformanceSetlistConcurrencyTest와 같은 방식).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class GroupWithdrawConcurrencyTest {

    private static final Long OWNER_ID = 10L;
    private static final Long WITHDRAWING_ID = 20L;

    @Autowired
    private GroupMemberService groupMemberService;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private GroupMemberRepository groupMemberRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        groupMemberRepository.deleteAllInBatch();
        groupRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("탈퇴 흐름이 모임장 여부를 확인한 뒤 위임이 먼저 커밋되면, 모임 탈퇴는 OWNER_CANNOT_LEAVE로 막히고 아무것도 바뀌지 않는다")
    void leaveAllGroups_seesDelegationCommittedAfterSnapshot() {
        Long groupId = groupRepository.save(Group.create("홍대 밴드부", null, GroupType.REGULAR, Region.SEOUL, null)).getId();
        groupMemberRepository.save(GroupMember.create(groupId, OWNER_ID, GroupRole.OWNER));
        groupMemberRepository.save(GroupMember.create(groupId, WITHDRAWING_ID, GroupRole.MEMBER));
        TransactionTemplate withdraw = new TransactionTemplate(transactionManager);
        TransactionTemplate delegate = new TransactionTemplate(transactionManager);
        delegate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> withdraw.executeWithoutResult(status -> {
            assertThat(groupMemberService.findOwnedGroups(WITHDRAWING_ID)).isEmpty();
            delegate.executeWithoutResult(inner ->
                    groupMemberService.changeRole(OWNER_ID, groupId, WITHDRAWING_ID, GroupRole.OWNER));
            groupMemberService.leaveAllGroups(WITHDRAWING_ID);
        })).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OWNER_CANNOT_LEAVE));

        assertThat(groupMemberRepository.findActiveOwner(groupId)).get()
                .satisfies(owner -> {
                    assertThat(owner.getUserId()).isEqualTo(WITHDRAWING_ID);
                    assertThat(owner.getStatus()).isEqualTo(GroupMemberStatus.ACTIVE);
                });
    }
}
