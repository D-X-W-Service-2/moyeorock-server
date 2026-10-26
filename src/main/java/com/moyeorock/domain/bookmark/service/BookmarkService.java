package com.moyeorock.domain.bookmark.service;

import com.moyeorock.domain.bookmark.dto.request.BookmarkCreateRequest;
import com.moyeorock.domain.bookmark.dto.response.BookmarkResponse;
import com.moyeorock.domain.bookmark.dto.response.BookmarkResponse.BookmarkTarget;
import com.moyeorock.domain.bookmark.entity.Bookmark;
import com.moyeorock.domain.bookmark.enums.BookmarkTargetType;
import com.moyeorock.domain.bookmark.repository.BookmarkRepository;
import com.moyeorock.domain.user.dto.response.UserProfileSummaryResponse;
import com.moyeorock.domain.user.service.UserService;
import com.moyeorock.global.common.dto.DeleteResponse;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>임시 구현 — USER 대상만 지원한다.</b> TEAM은 2팀 {@code TeamService}, SONG은 4팀 {@code SongService}가
 * 아직 없어서 대상 확인과 요약 조회를 할 수 없다. 두 서비스가 머지되면 {@link #create}의 대상 확인과
 * {@link #getBookmarks}의 대상 요약 조회에 해당 타입 분기를 추가한다(후속 이슈).
 */
@Service
@RequiredArgsConstructor
public class BookmarkService {

    // 명세의 저장 목록에는 정렬 파라미터가 없다 — 최신순 고정. 클라이언트 sort를 그대로 받으면
    // 엔티티에 없는 속성이 들어왔을 때 500이 난다.
    private static final Sort LATEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    // 대상별 UNIQUE 인덱스 이름 접두사(V2 마이그레이션). 이 제약 위반만 "중복"으로 본다.
    private static final String UNIQUE_KEY_PREFIX = "uk_bookmarks_";

    private final BookmarkRepository bookmarkRepository;
    private final UserService userService;

    /**
     * 저장 추가. 이미 저장한 대상이면 409다. 더블 탭·재전송·다른 기기에서 먼저 저장한 경우처럼 정상 흐름에서도
     * 일어나므로 프론트는 409를 "이미 저장됨"으로 취급하면 된다.
     */
    @Transactional
    public BookmarkResponse create(Long userId, BookmarkCreateRequest request) {
        // 임시: TEAM·SONG은 대상을 확인할 서비스가 없다
        if (request.targetType() != BookmarkTargetType.USER) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        Long targetUserId = request.targetId();
        UserProfileSummaryResponse target = userService.getProfileSummaries(List.of(targetUserId)).get(targetUserId);
        if (target == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (bookmarkRepository.existsByUserIdAndTargetUserId(userId, targetUserId)) {
            throw new BusinessException(ErrorCode.BOOKMARK_DUPLICATED);
        }
        Bookmark saved = saveOrThrowDuplicated(Bookmark.create(userId, null, targetUserId, null));
        return BookmarkResponse.of(saved, BookmarkTarget.fromUser(target));
    }

    /** 다른 도메인 서비스 호출이 섞여 있어 트랜잭션을 열지 않는다(conventions.md §3). */
    public PageResponse<BookmarkResponse> getBookmarks(Long userId, BookmarkTargetType targetType, Pageable pageable) {
        Pageable latestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), LATEST_FIRST);
        Page<Bookmark> page = findPage(userId, targetType, latestFirst);

        // 대상 타입별로 id를 모아 한 번씩만 조회한다(N+1 방지). 임시: USER만 해석한다.
        List<Long> userIds = page.getContent().stream()
                .map(Bookmark::getTargetUserId).filter(Objects::nonNull).toList();
        Map<Long, UserProfileSummaryResponse> users = userService.getProfileSummaries(userIds);

        return PageResponse.from(page.map(bookmark -> BookmarkResponse.of(bookmark, resolveTarget(bookmark, users))));
    }

    /** 저장은 실제로 행이 사라지는 hard delete다(합주·공지와 함께 3곳). */
    @Transactional
    public DeleteResponse delete(Long userId, Long bookmarkId) {
        Bookmark bookmark = bookmarkRepository.findByIdAndUserId(bookmarkId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOKMARK_NOT_FOUND));
        bookmarkRepository.delete(bookmark);
        return DeleteResponse.of(bookmarkId);
    }

    private Page<Bookmark> findPage(Long userId, BookmarkTargetType targetType, Pageable pageable) {
        if (targetType == null) {
            return bookmarkRepository.findByUserId(userId, pageable);
        }
        return switch (targetType) {
            case TEAM -> bookmarkRepository.findByUserIdAndTargetTeamIdIsNotNull(userId, pageable);
            case USER -> bookmarkRepository.findByUserIdAndTargetUserIdIsNotNull(userId, pageable);
            case SONG -> bookmarkRepository.findByUserIdAndTargetSongIdIsNotNull(userId, pageable);
        };
    }

    /** 요약을 못 찾은 대상(삭제·탈퇴·해체)과 아직 해석하지 못하는 타입(TEAM·SONG)은 null이다. */
    private BookmarkTarget resolveTarget(Bookmark bookmark, Map<Long, UserProfileSummaryResponse> users) {
        if (bookmark.getTargetType() == BookmarkTargetType.USER) {
            UserProfileSummaryResponse user = users.get(bookmark.getTargetUserId());
            return user == null ? null : BookmarkTarget.fromUser(user);
        }
        return null;
    }

    /** 사전 확인을 통과해도 동시 요청이 정확히 겹치면 UNIQUE에 걸린다 → 500 대신 409. */
    private Bookmark saveOrThrowDuplicated(Bookmark bookmark) {
        try {
            return bookmarkRepository.saveAndFlush(bookmark);
        } catch (DataIntegrityViolationException e) {
            String message = e.getMostSpecificCause().getMessage();
            if (message != null && message.contains(UNIQUE_KEY_PREFIX)) {
                throw new BusinessException(ErrorCode.BOOKMARK_DUPLICATED);
            }
            throw e;
        }
    }
}
