package com.moyeorock.domain.group.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.group.entity.Group;
import com.moyeorock.domain.group.entity.GroupMember;
import com.moyeorock.domain.group.enums.GroupRole;
import com.moyeorock.domain.group.enums.GroupStatus;
import com.moyeorock.domain.group.enums.GroupType;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.config.JpaAuditingConfig;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 해체된 모임을 "없는 모임"으로 거르는 것이 이 조회들에 달려 있어서(모임 상세·수정, 모임원·공지 권한 확인)
 * 실제 MySQL에 저장된 status 문자열로 걸러지는지 본다. 내 모임 목록은 group_members 조인·LIKE 이스케이프·
 * 정렬·count 쿼리가 실제 SQL로 맞는지 본다.
 */
@DataJpaTest
@Import({JpaAuditingConfig.class, TestcontainersConfig.class})
class GroupRepositoryTest {

    private static final Long ME = 1L;
    private static final Long OTHER = 2L;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Test
    @DisplayName("해체된 모임은 ACTIVE로 찾으면 없는 모임이다")
    void findByIdAndStatus_excludesDisbandedGroup() {
        Group group = groupRepository.save(group("홍대 밴드부"));
        group.disband();
        groupRepository.flush();

        assertThat(groupRepository.findByIdAndStatus(group.getId(), GroupStatus.ACTIVE)).isEmpty();
        assertThat(groupRepository.existsByIdAndStatus(group.getId(), GroupStatus.ACTIVE)).isFalse();
    }

    @Test
    @DisplayName("새로 만든 모임은 ACTIVE로 저장돼 찾을 수 있다")
    void findByIdAndStatus_findsActiveGroup() {
        Group group = groupRepository.saveAndFlush(group("홍대 밴드부"));

        assertThat(groupRepository.findByIdAndStatus(group.getId(), GroupStatus.ACTIVE)).isPresent();
        assertThat(groupRepository.existsByIdAndStatus(group.getId(), GroupStatus.ACTIVE)).isTrue();
    }

    @Test
    @DisplayName("내 모임 목록에는 내가 ACTIVE 모임원인 모임만 나온다 — 남의 모임, 탈퇴·강퇴한 모임은 빠진다")
    void findMyGroups_returnsOnlyMyActiveMemberships() {
        Group mine = joined(ME, group("내 모임"), 1);
        joined(OTHER, group("남의 모임"), 2);
        GroupMember left = groupMemberRepository.save(GroupMember.create(
                groupRepository.save(group("탈퇴한 모임")).getId(), ME, GroupRole.MEMBER));
        left.leave();
        GroupMember banned = groupMemberRepository.save(GroupMember.create(
                groupRepository.save(group("강퇴된 모임")).getId(), ME, GroupRole.MEMBER));
        banned.ban();
        groupMemberRepository.flush();

        Page<Group> page = groupRepository.findMyGroups(ME, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Group::getId).containsExactly(mine.getId());
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("해체된 모임은 내 모임 목록에서 빠진다")
    void findMyGroups_excludesDisbandedGroups() {
        Group active = joined(ME, group("활동 중"), 1);
        Group disbanded = joined(ME, group("해체됨"), 2);
        disbanded.disband();
        groupRepository.flush();

        Page<Group> page = groupRepository.findMyGroups(ME, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Group::getId).containsExactly(active.getId());
    }

    @Test
    @DisplayName("keyword는 모임 이름 부분 일치다")
    void findMyGroups_filtersByNameContaining() {
        Group band = joined(ME, group("홍대 밴드부"), 1);
        joined(ME, group("재즈 동아리"), 2);

        Page<Group> page = groupRepository.findMyGroups(ME, "밴드", PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Group::getId).containsExactly(band.getId());
    }

    @Test
    @DisplayName("keyword의 %·_는 와일드카드가 아니라 글자 그대로 찾는다")
    void findMyGroups_treatsLikeWildcardsLiterally() {
        Group percent = joined(ME, group("100% 밴드"), 1);
        joined(ME, group("1000 밴드"), 2);
        Group underscore = joined(ME, group("a_b"), 3);
        joined(ME, group("axb"), 4);

        assertThat(groupRepository.findMyGroups(ME, "0%", PageRequest.of(0, 20)).getContent())
                .extracting(Group::getId).containsExactly(percent.getId());
        assertThat(groupRepository.findMyGroups(ME, "a_b", PageRequest.of(0, 20)).getContent())
                .extracting(Group::getId).containsExactly(underscore.getId());
    }

    @Test
    @DisplayName("가입 최신순으로 정렬되고 페이지를 나눠도 전체 개수가 맞다")
    void findMyGroups_ordersByJoinedAtDesc_andCountsAcrossPages() {
        Group first = joined(ME, group("가장 먼저 가입"), 1);
        Group second = joined(ME, group("두 번째 가입"), 2);
        Group latest = joined(ME, group("가장 최근 가입"), 3);

        Page<Group> firstPage = groupRepository.findMyGroups(ME, null, PageRequest.of(0, 2));
        Page<Group> secondPage = groupRepository.findMyGroups(ME, null, PageRequest.of(1, 2));

        assertThat(firstPage.getContent()).extracting(Group::getId).containsExactly(latest.getId(), second.getId());
        assertThat(secondPage.getContent()).extracting(Group::getId).containsExactly(first.getId());
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
    }

    // joinedAt은 create()가 now()로 채워 연달아 만들면 같아질 수 있다 — 정렬을 확정하려고 가입 순서를 분 단위로 벌린다.
    private Group joined(Long userId, Group group, int joinOrder) {
        Group saved = groupRepository.save(group);
        GroupMember member = GroupMember.create(saved.getId(), userId, GroupRole.MEMBER);
        ReflectionTestUtils.setField(member, "joinedAt", LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(joinOrder));
        groupMemberRepository.saveAndFlush(member);
        return saved;
    }

    private static Group group(String name) {
        return Group.create(name, null, GroupType.REGULAR, Region.SEOUL, null);
    }
}
