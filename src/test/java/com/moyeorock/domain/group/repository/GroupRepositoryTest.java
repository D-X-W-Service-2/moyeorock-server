package com.moyeorock.domain.group.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.group.entity.Group;
import com.moyeorock.domain.group.enums.GroupStatus;
import com.moyeorock.domain.group.enums.GroupType;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * 해체된 모임을 "없는 모임"으로 거르는 것이 이 조회들에 달려 있어서(모임 상세·수정, 모임원·공지 권한 확인)
 * 실제 MySQL에 저장된 status 문자열로 걸러지는지 본다.
 */
@DataJpaTest
@Import({JpaAuditingConfig.class, TestcontainersConfig.class})
class GroupRepositoryTest {

    @Autowired
    private GroupRepository groupRepository;

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

    private static Group group(String name) {
        return Group.create(name, null, GroupType.REGULAR, Region.SEOUL, null);
    }
}
