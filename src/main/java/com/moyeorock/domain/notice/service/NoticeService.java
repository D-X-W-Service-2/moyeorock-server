package com.moyeorock.domain.notice.service;

import com.moyeorock.domain.group.service.GroupMemberService;
import com.moyeorock.domain.notice.dto.request.NoticeCreateRequest;
import com.moyeorock.domain.notice.dto.request.NoticeUpdateRequest;
import com.moyeorock.domain.notice.dto.response.NoticeDetailResponse;
import com.moyeorock.domain.notice.dto.response.NoticePreviewResponse;
import com.moyeorock.domain.notice.dto.response.NoticeSummaryResponse;
import com.moyeorock.domain.notice.entity.GroupNotice;
import com.moyeorock.domain.notice.repository.GroupNoticeRepository;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공지 담당. 작성·수정·삭제 권한은 작성자가 아니라 <b>현재 모임장</b>이다 — 작성자가 전 모임장이어도
 * 현 모임장만 수정·삭제할 수 있다. 조회는 모임원이면 가능하다.
 */
@Service
@RequiredArgsConstructor
public class NoticeService {

    private static final int PREVIEW_SIZE = 3;

    private final GroupNoticeRepository groupNoticeRepository;
    private final GroupMemberService groupMemberService;

    /** 정렬은 고정 공지 우선·최신순 고정이라 클라이언트 sort는 버린다(엔티티에 없는 속성이 들어오면 500이 난다). */
    public PageResponse<NoticeSummaryResponse> getNotices(Long userId, Long groupId, Boolean isPinned,
            Pageable pageable) {
        groupMemberService.ensureActiveMember(groupId, userId);
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<GroupNotice> notices = isPinned == null
                ? groupNoticeRepository.findByGroupIdOrderByIsPinnedDescCreatedAtDescIdDesc(groupId, unsorted)
                : groupNoticeRepository.findByGroupIdAndIsPinnedOrderByCreatedAtDescIdDesc(groupId, isPinned, unsorted);
        return PageResponse.from(notices.map(NoticeSummaryResponse::from));
    }

    public NoticeDetailResponse getNotice(Long userId, Long noticeId) {
        GroupNotice notice = getNoticeOrThrow(noticeId);
        groupMemberService.ensureActiveMember(notice.getGroupId(), userId);
        return NoticeDetailResponse.from(notice);
    }

    /**
     * 동아리 홈의 고정 공지 미리보기 — <b>고정된 공지만</b> 최신순 {@value #PREVIEW_SIZE}건.
     * 고정 공지가 없으면 빈 목록이다(일반 공지로 채우지 않는다 — 응답 필드가 {@code pinnedNotices}다).
     * 해체된 모임이면 {@code GROUP_NOT_FOUND}다. <b>모임원 확인은 하지 않는다</b> — 모임 상세·수정에서만
     * 호출하고 그쪽에서 이미 모임원·모임장인지 확인한다.
     */
    public List<NoticePreviewResponse> getPinnedPreview(Long groupId) {
        groupMemberService.ensureGroupExists(groupId);
        return groupNoticeRepository
                .findByGroupIdAndIsPinnedTrueOrderByCreatedAtDescIdDesc(groupId, Limit.of(PREVIEW_SIZE)).stream()
                .map(NoticePreviewResponse::from)
                .toList();
    }

    @Transactional
    public NoticeDetailResponse create(Long userId, Long groupId, NoticeCreateRequest request) {
        groupMemberService.ensureOwner(groupId, userId);
        GroupNotice notice = groupNoticeRepository.save(
                GroupNotice.create(groupId, userId, request.title(), request.body(), request.pinned()));
        return NoticeDetailResponse.from(notice);
    }

    @Transactional
    public NoticeDetailResponse update(Long userId, Long noticeId, NoticeUpdateRequest request) {
        GroupNotice notice = getNoticeOrThrow(noticeId);
        groupMemberService.ensureOwner(notice.getGroupId(), userId);
        notice.update(request.title(), request.body(), request.pinned());
        // updatedAt이 갱신된 값으로 응답에 나가도록 flush 후 변환한다
        groupNoticeRepository.flush();
        return NoticeDetailResponse.from(notice);
    }

    @Transactional
    public void delete(Long userId, Long noticeId) {
        GroupNotice notice = getNoticeOrThrow(noticeId);
        groupMemberService.ensureOwner(notice.getGroupId(), userId);
        groupNoticeRepository.delete(notice);
    }

    private GroupNotice getNoticeOrThrow(Long noticeId) {
        return groupNoticeRepository.findById(noticeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));
    }
}
