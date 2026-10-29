package com.moyeorock.domain.group.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.group.entity.Group;
import com.moyeorock.domain.group.entity.GroupMember;
import com.moyeorock.domain.group.enums.GroupMemberStatus;
import com.moyeorock.domain.group.enums.GroupRole;
import com.moyeorock.domain.group.enums.GroupType;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.config.JpaAuditingConfig;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 정렬이 핵심이라 실제 MySQL에서 검증한다 — {@code role}이 문자열로 저장돼 있어
 * {@code ORDER BY role}로는 OWNER가 맨 뒤(MANAGER·MEMBER·OWNER)로 가는 것을 CASE로 막았는지 본다.
 * 모임 상태를 조인하는 조회는 실제 모임 행이 있어야 해서 모임을 저장한다({@code groups_}가 created_at·updated_at
 * NOT NULL이라 auditing을 켠다).
 */
@DataJpaTest
@Import({JpaAuditingConfig.class, TestcontainersConfig.class})
class GroupMemberRepositoryTest {

    private static final Long GROUP_ID = 1L;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private GroupRepository groupRepository;

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
    @DisplayName("가입 시각이 같으면 id 순이라 페이지를 나눠도 같은 사람이 두 번 나오거나 빠지지 않는다")
    void findActiveMembers_breaksJoinedAtTiesById() {
        LocalDateTime sameTime = LocalDateTime.of(2026, 1, 1, 0, 0);
        List<GroupMember> saved = groupMemberRepository.saveAll(List.of(
                joinedAt(GroupMember.create(GROUP_ID, 30L, GroupRole.MEMBER), sameTime),
                joinedAt(GroupMember.create(GROUP_ID, 20L, GroupRole.MEMBER), sameTime),
                joinedAt(GroupMember.create(GROUP_ID, 40L, GroupRole.MEMBER), sameTime)));

        List<Long> pagedIds = new ArrayList<>();
        for (int page = 0; page < saved.size(); page++) {
            groupMemberRepository.findActiveMembers(GROUP_ID, GroupMemberStatus.ACTIVE, PageRequest.of(page, 1))
                    .forEach(member -> pagedIds.add(member.getId()));
        }

        assertThat(pagedIds).containsExactlyElementsOf(saved.stream().map(GroupMember::getId).sorted().toList());
    }

    @Test
    @DisplayName("탈퇴·강퇴된 모임원은 목록에도 총 개수에도 들어가지 않는다")
    void findActiveMembers_excludesLeftAndBanned() {
        GroupMember left = GroupMember.create(GROUP_ID, 40L, GroupRole.MEMBER);
        left.leave();
        GroupMember banned = GroupMember.create(GROUP_ID, 50L, GroupRole.MEMBER);
        banned.ban();
        groupMemberRepository.saveAll(List.of(
                GroupMember.create(GROUP_ID, 10L, GroupRole.OWNER), left, banned));

        var page = groupMemberRepository.findActiveMembers(GROUP_ID, GroupMemberStatus.ACTIVE, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(GroupMember::getUserId).containsExactly(10L);
    }

    @Test
    @DisplayName("ACTIVE 모임원 수만 센다")
    void countActiveMembers_countsOnlyActive() {
        Long groupId = savedGroup().getId();
        GroupMember left = GroupMember.create(groupId, 40L, GroupRole.MEMBER);
        left.leave();
        groupMemberRepository.saveAll(List.of(
                GroupMember.create(groupId, 10L, GroupRole.OWNER),
                GroupMember.create(groupId, 20L, GroupRole.MEMBER), left));

        assertThat(groupMemberRepository.countActiveMembers(groupId)).isEqualTo(2);
    }

    @Test
    @DisplayName("모임장을 역할로 찾을 수 있다")
    void findActiveOwner_findsOwner() {
        Long groupId = savedGroup().getId();
        groupMemberRepository.saveAll(List.of(
                GroupMember.create(groupId, 10L, GroupRole.OWNER),
                GroupMember.create(groupId, 20L, GroupRole.MANAGER)));

        assertThat(groupMemberRepository.findActiveOwner(groupId))
                .get()
                .extracting(GroupMember::getUserId)
                .isEqualTo(10L);
    }

    @Test
    @DisplayName("해체된 모임의 모임원은 ACTIVE로 남아 있어도 찾거나 세지 않는다")
    void lookups_excludeDisbandedGroup() {
        Group group = savedGroup();
        Long groupId = group.getId();
        groupMemberRepository.saveAll(List.of(
                GroupMember.create(groupId, 10L, GroupRole.OWNER),
                GroupMember.create(groupId, 20L, GroupRole.MEMBER)));
        group.disband();
        groupRepository.flush();

        assertThat(groupMemberRepository.findActiveMember(groupId, 20L)).isEmpty();
        assertThat(groupMemberRepository.findActiveOwner(groupId)).isEmpty();
        assertThat(groupMemberRepository.countActiveMembers(groupId)).isZero();
    }

    @Test
    @DisplayName("해체되지 않은 모임에서는 ACTIVE 모임원을 찾는다")
    void findActiveMember_findsMemberOfActiveGroup() {
        Long groupId = savedGroup().getId();
        groupMemberRepository.save(GroupMember.create(groupId, 20L, GroupRole.MEMBER));

        assertThat(groupMemberRepository.findActiveMember(groupId, 20L))
                .get()
                .extracting(GroupMember::getRole)
                .isEqualTo(GroupRole.MEMBER);
    }

    private static GroupMember joinedAt(GroupMember member, LocalDateTime joinedAt) {
        ReflectionTestUtils.setField(member, "joinedAt", joinedAt);
        return member;
    }

    private Group savedGroup() {
        return groupRepository.save(Group.create("홍대 밴드부", null, GroupType.REGULAR, Region.SEOUL, null));
    }
}
