package com.moyeorock.domain.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moyeorock.domain.group.dto.request.GroupCreateRequest;
import com.moyeorock.domain.group.dto.request.GroupUpdateRequest;
import com.moyeorock.domain.group.dto.response.GroupDetailResponse;
import com.moyeorock.domain.group.entity.Group;
import com.moyeorock.domain.group.enums.GroupRole;
import com.moyeorock.domain.group.enums.GroupType;
import com.moyeorock.domain.group.repository.GroupRepository;
import com.moyeorock.domain.notice.dto.response.NoticePreviewResponse;
import com.moyeorock.domain.notice.service.NoticeService;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    private static final Long GROUP_ID = 1L;
    private static final Long OWNER_ID = 10L;
    private static final Long MEMBER_ID = 20L;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberService groupMemberService;

    @Mock
    private NoticeService noticeService;

    private GroupService groupService;

    @BeforeEach
    void setUp() {
        groupService = new GroupService(groupRepository, groupMemberService, noticeService);
    }

    @Test
    @DisplayName("모임을 만들면 생성자가 모임장으로 등록되고 모임원 수가 1이다")
    void create_registersCreatorAsOwner() {
        when(groupRepository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), GROUP_ID));
        when(noticeService.getPinnedPreview(GROUP_ID)).thenReturn(List.of());

        GroupDetailResponse response = groupService.create(OWNER_ID, new GroupCreateRequest(
                "홍대 밴드부", "매주 토요일 합주", GroupType.REGULAR, Region.SEOUL, "https://x/cover.jpg"));

        verify(groupMemberService).addOwner(GROUP_ID, OWNER_ID);
        assertThat(response.id()).isEqualTo(GROUP_ID);
        assertThat(response.ownerId()).isEqualTo(OWNER_ID);
        assertThat(response.myRole()).isEqualTo(GroupRole.OWNER);
        assertThat(response.memberCount()).isEqualTo(1);
        assertThat(response.name()).isEqualTo("홍대 밴드부");
    }

    @Test
    @DisplayName("모임 상세는 모임원 수·내 역할·고정 공지를 함께 담는다")
    void getDetail_composesHomeScreen() {
        Group group = withId(group(), GROUP_ID);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(groupMemberService.getActiveRole(GROUP_ID, MEMBER_ID)).thenReturn(GroupRole.MEMBER);
        when(groupMemberService.getOwnerId(GROUP_ID)).thenReturn(OWNER_ID);
        when(groupMemberService.countActiveMembers(GROUP_ID)).thenReturn(24);
        when(noticeService.getPinnedPreview(GROUP_ID)).thenReturn(List.of(
                new NoticePreviewResponse(8L, "3월 정기공연 안내", true, LocalDateTime.now())));

        GroupDetailResponse response = groupService.getDetail(MEMBER_ID, GROUP_ID);

        assertThat(response.myRole()).isEqualTo(GroupRole.MEMBER);
        assertThat(response.memberCount()).isEqualTo(24);
        assertThat(response.ownerId()).isEqualTo(OWNER_ID);
        assertThat(response.pinnedNotices()).hasSize(1);
        assertThat(response.pinnedNotices().get(0).title()).isEqualTo("3월 정기공연 안내");
    }

    @Test
    @DisplayName("없는 모임을 조회하면 GROUP_NOT_FOUND를 던진다")
    void getDetail_throws_whenGroupMissing() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.getDetail(MEMBER_ID, GROUP_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.GROUP_NOT_FOUND);
        verifyNoInteractions(noticeService);
    }

    @Test
    @DisplayName("모임 정보 수정은 type까지 반영한다")
    void update_appliesAllFieldsIncludingType() {
        Group group = withId(group(), GROUP_ID);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(groupMemberService.getOwnerId(GROUP_ID)).thenReturn(OWNER_ID);
        when(groupMemberService.countActiveMembers(GROUP_ID)).thenReturn(5);
        when(noticeService.getPinnedPreview(GROUP_ID)).thenReturn(List.of());

        GroupDetailResponse response = groupService.update(OWNER_ID, GROUP_ID, new GroupUpdateRequest(
                "새 이름", "새 소개", GroupType.PROJECT, Region.GYEONGGI, null));

        verify(groupMemberService).ensureOwner(GROUP_ID, OWNER_ID);
        assertThat(group.getType()).isEqualTo(GroupType.PROJECT);
        assertThat(group.getRegion()).isEqualTo(Region.GYEONGGI);
        assertThat(group.getCoverImage()).isNull();
        assertThat(response.name()).isEqualTo("새 이름");
        assertThat(response.myRole()).isEqualTo(GroupRole.OWNER);
    }

    private static Group group() {
        return Group.create("홍대 밴드부", "소개", GroupType.REGULAR, Region.SEOUL, "https://x/cover.jpg");
    }

    // id는 GeneratedValue라 create()로는 못 채운다 — 저장 후 채워지는 것처럼 리플렉션으로 넣는다.
    private static Group withId(Group group, Long id) {
        try {
            Field idField = Group.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(group, id);
            return group;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
