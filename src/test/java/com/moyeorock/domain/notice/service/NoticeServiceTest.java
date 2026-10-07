package com.moyeorock.domain.notice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moyeorock.domain.group.service.GroupMemberService;
import com.moyeorock.domain.notice.dto.request.NoticeCreateRequest;
import com.moyeorock.domain.notice.dto.request.NoticeUpdateRequest;
import com.moyeorock.domain.notice.dto.response.NoticeDetailResponse;
import com.moyeorock.domain.notice.entity.GroupNotice;
import com.moyeorock.domain.notice.repository.GroupNoticeRepository;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    private static final Long GROUP_ID = 1L;
    private static final Long NOTICE_ID = 100L;
    private static final Long OWNER_ID = 10L;
    private static final Long FORMER_OWNER_ID = 11L;

    @Mock
    private GroupNoticeRepository groupNoticeRepository;

    @Mock
    private GroupMemberService groupMemberService;

    private NoticeService noticeService;

    @BeforeEach
    void setUp() {
        noticeService = new NoticeService(groupNoticeRepository, groupMemberService);
    }

    @Test
    @DisplayName("모임장이면 공지를 작성할 수 있고 작성자 아이디가 저장된다")
    void create_savesNoticeWithAuthorId() {
        when(groupNoticeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        NoticeDetailResponse response = noticeService.create(
                OWNER_ID, GROUP_ID, new NoticeCreateRequest("제목", "본문", true));

        verify(groupMemberService).ensureOwner(GROUP_ID, OWNER_ID);
        var captor = org.mockito.ArgumentCaptor.forClass(GroupNotice.class);
        verify(groupNoticeRepository).save(captor.capture());
        assertThat(captor.getValue().getAuthorId()).isEqualTo(OWNER_ID);
        assertThat(captor.getValue().isPinned()).isTrue();
        assertThat(response.title()).isEqualTo("제목");
    }

    @Test
    @DisplayName("isPinned를 안 보내면 고정되지 않은 공지로 저장된다")
    void create_defaultsToUnpinned_whenIsPinnedOmitted() {
        when(groupNoticeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        noticeService.create(OWNER_ID, GROUP_ID, new NoticeCreateRequest("제목", "본문", null));

        var captor = org.mockito.ArgumentCaptor.forClass(GroupNotice.class);
        verify(groupNoticeRepository).save(captor.capture());
        assertThat(captor.getValue().isPinned()).isFalse();
    }

    @Test
    @DisplayName("모임장이 아니면 공지를 작성할 수 없고 저장도 하지 않는다")
    void create_throws_whenNotOwner() {
        doThrow(new BusinessException(ErrorCode.NOT_GROUP_OWNER))
                .when(groupMemberService).ensureOwner(GROUP_ID, FORMER_OWNER_ID);

        assertThatThrownBy(() -> noticeService.create(
                FORMER_OWNER_ID, GROUP_ID, new NoticeCreateRequest("제목", "본문", false)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_GROUP_OWNER);
        verifyNoInteractions(groupNoticeRepository);
    }

    @Test
    @DisplayName("작성자였어도 현재 모임장이 아니면 자기 공지를 수정할 수 없다")
    void update_throws_whenAuthorIsNoLongerOwner() {
        GroupNotice notice = notice(FORMER_OWNER_ID);
        when(groupNoticeRepository.findById(NOTICE_ID)).thenReturn(Optional.of(notice));
        doThrow(new BusinessException(ErrorCode.NOT_GROUP_OWNER))
                .when(groupMemberService).ensureOwner(GROUP_ID, FORMER_OWNER_ID);

        assertThatThrownBy(() -> noticeService.update(
                FORMER_OWNER_ID, NOTICE_ID, new NoticeUpdateRequest("수정", "본문", true)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_GROUP_OWNER);
        assertThat(notice.getTitle()).isEqualTo("원래 제목");
    }

    @Test
    @DisplayName("전 모임장이 쓴 공지도 현재 모임장이면 수정할 수 있다")
    void update_succeeds_whenCurrentOwnerEditsFormerOwnersNotice() {
        GroupNotice notice = notice(FORMER_OWNER_ID);
        when(groupNoticeRepository.findById(NOTICE_ID)).thenReturn(Optional.of(notice));

        NoticeDetailResponse response = noticeService.update(
                OWNER_ID, NOTICE_ID, new NoticeUpdateRequest("수정된 제목", "수정된 본문", true));

        verify(groupMemberService).ensureOwner(GROUP_ID, OWNER_ID);
        assertThat(response.title()).isEqualTo("수정된 제목");
        assertThat(notice.getBody()).isEqualTo("수정된 본문");
    }

    @Test
    @DisplayName("없는 공지를 수정하려 하면 NOTICE_NOT_FOUND를 던지고 권한 확인까지 가지 않는다")
    void update_throws_whenNoticeMissing() {
        when(groupNoticeRepository.findById(NOTICE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noticeService.update(
                OWNER_ID, NOTICE_ID, new NoticeUpdateRequest("제목", "본문", false)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOTICE_NOT_FOUND);
        verify(groupMemberService, never()).ensureOwner(any(), any());
    }

    @Test
    @DisplayName("모임장이면 공지를 삭제할 수 있다")
    void delete_removesNotice() {
        GroupNotice notice = notice(OWNER_ID);
        when(groupNoticeRepository.findById(NOTICE_ID)).thenReturn(Optional.of(notice));

        noticeService.delete(OWNER_ID, NOTICE_ID);

        verify(groupMemberService).ensureOwner(GROUP_ID, OWNER_ID);
        verify(groupNoticeRepository).delete(notice);
    }

    @Test
    @DisplayName("공지 상세는 모임원이면 조회할 수 있다")
    void getNotice_returnsDetail_forGroupMember() {
        GroupNotice notice = notice(OWNER_ID);
        when(groupNoticeRepository.findById(NOTICE_ID)).thenReturn(Optional.of(notice));

        NoticeDetailResponse response = noticeService.getNotice(30L, NOTICE_ID);

        verify(groupMemberService).ensureActiveMember(GROUP_ID, 30L);
        assertThat(response.body()).isEqualTo("원래 본문");
        assertThat(response.groupId()).isEqualTo(GROUP_ID);
    }

    @Test
    @DisplayName("모임원이 아니면 공지 상세를 조회할 수 없다")
    void getNotice_throws_whenNotGroupMember() {
        when(groupNoticeRepository.findById(NOTICE_ID)).thenReturn(Optional.of(notice(OWNER_ID)));
        doThrow(new BusinessException(ErrorCode.NOT_GROUP_MEMBER))
                .when(groupMemberService).ensureActiveMember(GROUP_ID, 99L);

        assertThatThrownBy(() -> noticeService.getNotice(99L, NOTICE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
    }

    private static GroupNotice notice(Long authorId) {
        return GroupNotice.create(GROUP_ID, authorId, "원래 제목", "원래 본문", false);
    }
}
