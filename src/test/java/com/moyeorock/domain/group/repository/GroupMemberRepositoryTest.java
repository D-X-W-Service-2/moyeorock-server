package com.moyeorock.domain.group.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.group.entity.GroupMember;
import com.moyeorock.domain.group.enums.GroupMemberStatus;
import com.moyeorock.domain.group.enums.GroupRole;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

/**
 * 정렬이 핵심이라 실제 MySQL에서 검증한다 — {@code role}이 문자열로 저장돼 있어
 * {@code ORDER BY role}로는 OWNER가 맨 뒤(MANAGER·MEMBER·OWNER)로 가는 것을 CASE로 막았는지 본다.
 */
@DataJpaTest
@Import(TestcontainersConfig.class)
class GroupMemberRepositoryTest {

    private static final Long GROUP_ID = 1L;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Test
    @DisplayName("모임원 목록은 역할 문자열 순서와 무관하게 모임장이 맨 위에 온다")
    void findActiveMembers_putsOwnerFirst() {
        groupMemberRepository.saveAll(List.of(
                GroupMember.create(GROUP_ID, 30L, GroupRole.MEMBER),
                GroupMember.create(GROUP_ID, 20L, GroupRole.MANAGER),
                GroupMember.create(GROUP_ID, 10L, GroupRole.OWNER)));

        List<GroupMember> members = groupMemberRepository
                .findActiveMembers(GROUP_ID, GroupMemberStatus.ACTIVE, PageRequest.of(0, 20))
                .getContent();

        assertThat(members).hasSize(3);
        assertThat(members.get(0).getUserId()).isEqualTo(10L);
        assertThat(members.get(0).getRole()).isEqualTo(GroupRole.OWNER);
        assertThat(members).extracting(GroupMember::getUserId).containsExactlyInAnyOrder(10L, 20L, 30L);
    }

    @Test
    @DisplayName("탈퇴·강퇴된 모임원은 목록에도 총 개수에도 들어가지 않는다")
    void findActiveMembers_excludesLeftAndBanned() {
        GroupMember left = GroupMember.create(GROUP_ID, 40L, GroupRole.MEMBER);
        left.updateStatus(GroupMemberStatus.LEFT);
        GroupMember banned = GroupMember.create(GROUP_ID, 50L, GroupRole.MEMBER);
        banned.updateStatus(GroupMemberStatus.BANNED);
        groupMemberRepository.saveAll(List.of(
                GroupMember.create(GROUP_ID, 10L, GroupRole.OWNER), left, banned));

        var page = groupMemberRepository.findActiveMembers(GROUP_ID, GroupMemberStatus.ACTIVE, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(GroupMember::getUserId).containsExactly(10L);
    }

    @Test
    @DisplayName("ACTIVE 모임원 수만 센다")
    void countByGroupIdAndStatus_countsOnlyActive() {
        GroupMember left = GroupMember.create(GROUP_ID, 40L, GroupRole.MEMBER);
        left.updateStatus(GroupMemberStatus.LEFT);
        groupMemberRepository.saveAll(List.of(
                GroupMember.create(GROUP_ID, 10L, GroupRole.OWNER),
                GroupMember.create(GROUP_ID, 20L, GroupRole.MEMBER), left));

        assertThat(groupMemberRepository.countByGroupIdAndStatus(GROUP_ID, GroupMemberStatus.ACTIVE)).isEqualTo(2);
    }

    @Test
    @DisplayName("모임장을 역할로 찾을 수 있다")
    void findByGroupIdAndRoleAndStatus_findsOwner() {
        groupMemberRepository.saveAll(List.of(
                GroupMember.create(GROUP_ID, 10L, GroupRole.OWNER),
                GroupMember.create(GROUP_ID, 20L, GroupRole.MANAGER)));

        assertThat(groupMemberRepository
                .findByGroupIdAndRoleAndStatus(GROUP_ID, GroupRole.OWNER, GroupMemberStatus.ACTIVE))
                .get()
                .extracting(GroupMember::getUserId)
                .isEqualTo(10L);
    }
}
