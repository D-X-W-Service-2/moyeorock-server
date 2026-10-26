package com.moyeorock.domain.bookmark.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.moyeorock.domain.bookmark.dto.request.BookmarkCreateRequest;
import com.moyeorock.domain.bookmark.dto.response.BookmarkResponse;
import com.moyeorock.domain.bookmark.entity.Bookmark;
import com.moyeorock.domain.bookmark.enums.BookmarkTargetType;
import com.moyeorock.domain.bookmark.repository.BookmarkRepository;
import com.moyeorock.domain.user.dto.response.UserProfileSummaryResponse;
import com.moyeorock.domain.user.service.UserService;
import com.moyeorock.global.common.dto.DeleteResponse;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BookmarkServiceTest {

    private static final Long ME = 1L;
    private static final Long TARGET = 5L;

    @Mock
    private BookmarkRepository bookmarkRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private BookmarkService bookmarkService;

    private static UserProfileSummaryResponse profile(Long id) {
        return new UserProfileSummaryResponse(id, "민서", Region.SEOUL, List.of(Genre.JAZZ), "https://img/c.jpg");
    }

    private static Bookmark userBookmark(Long id, Long targetUserId) {
        Bookmark bookmark = Bookmark.create(ME, null, targetUserId, null);
        ReflectionTestUtils.setField(bookmark, "id", id);
        ReflectionTestUtils.setField(bookmark, "createdAt", LocalDateTime.of(2026, 8, 10, 12, 0));
        return bookmark;
    }

    private static BusinessException thrownBy(Runnable action) {
        return (BusinessException) catchThrowable(action::run);
    }

    @Test
    @DisplayName("USER 대상을 저장하면 대상 요약(닉네임·지역·장르·이미지)과 함께 돌려준다")
    void create_userTarget() {
        given(userService.getProfileSummaries(List.of(TARGET))).willReturn(Map.of(TARGET, profile(TARGET)));
        given(bookmarkRepository.existsByUserIdAndTargetUserId(ME, TARGET)).willReturn(false);
        given(bookmarkRepository.saveAndFlush(any())).willReturn(userBookmark(21L, TARGET));

        BookmarkResponse response = bookmarkService.create(ME, new BookmarkCreateRequest(BookmarkTargetType.USER, TARGET));

        assertThat(response.id()).isEqualTo(21L);
        assertThat(response.targetType()).isEqualTo(BookmarkTargetType.USER);
        assertThat(response.targetId()).isEqualTo(TARGET);
        assertThat(response.target().name()).isEqualTo("민서");
        assertThat(response.target().region()).isEqualTo(Region.SEOUL);
        assertThat(response.target().genres()).containsExactly(Genre.JAZZ);
        assertThat(response.target().imageUrl()).isEqualTo("https://img/c.jpg");
        ArgumentCaptor<Bookmark> saved = ArgumentCaptor.forClass(Bookmark.class);
        verify(bookmarkRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(ME);
        assertThat(saved.getValue().getTargetUserId()).isEqualTo(TARGET);
        assertThat(saved.getValue().getTargetTeamId()).isNull();
        assertThat(saved.getValue().getTargetSongId()).isNull();
    }

    @Test
    @DisplayName("자기 자신도 저장할 수 있다 (명세에 금지가 없다)")
    void create_selfIsAllowed() {
        given(userService.getProfileSummaries(List.of(ME))).willReturn(Map.of(ME, profile(ME)));
        given(bookmarkRepository.saveAndFlush(any())).willReturn(userBookmark(22L, ME));

        BookmarkResponse response = bookmarkService.create(ME, new BookmarkCreateRequest(BookmarkTargetType.USER, ME));

        assertThat(response.targetId()).isEqualTo(ME);
    }

    @Test
    @DisplayName("임시: TEAM·SONG 저장은 대상을 확인할 서비스가 없어 VALIDATION_FAILED이고 아무것도 호출하지 않는다")
    void create_teamAndSongAreRejected() {
        assertThat(thrownBy(() -> bookmarkService.create(ME, new BookmarkCreateRequest(BookmarkTargetType.TEAM, 5L)))
                .getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(thrownBy(() -> bookmarkService.create(ME, new BookmarkCreateRequest(BookmarkTargetType.SONG, 5L)))
                .getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);

        verifyNoInteractions(userService);
        verify(bookmarkRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("없는 사용자(또는 탈퇴한 사용자)는 USER_NOT_FOUND이고 저장하지 않는다")
    void create_targetMissing() {
        given(userService.getProfileSummaries(List.of(TARGET))).willReturn(Map.of());

        assertThat(thrownBy(() -> bookmarkService.create(ME, new BookmarkCreateRequest(BookmarkTargetType.USER, TARGET)))
                .getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
        verify(bookmarkRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("이미 저장한 대상이면 BOOKMARK_DUPLICATED(409)이고 저장하지 않는다")
    void create_duplicatedByPreCheck() {
        given(userService.getProfileSummaries(List.of(TARGET))).willReturn(Map.of(TARGET, profile(TARGET)));
        given(bookmarkRepository.existsByUserIdAndTargetUserId(ME, TARGET)).willReturn(true);

        assertThat(thrownBy(() -> bookmarkService.create(ME, new BookmarkCreateRequest(BookmarkTargetType.USER, TARGET)))
                .getErrorCode()).isEqualTo(ErrorCode.BOOKMARK_DUPLICATED);
        verify(bookmarkRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("동시 요청이 사전 확인을 통과해 UNIQUE에 걸려도 500이 아니라 BOOKMARK_DUPLICATED다")
    void create_duplicatedByUniqueRace() {
        given(userService.getProfileSummaries(List.of(TARGET))).willReturn(Map.of(TARGET, profile(TARGET)));
        given(bookmarkRepository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("dup",
                new RuntimeException("Duplicate entry '1-5' for key 'bookmarks.uk_bookmarks_user_id_target_user_id'")));

        assertThat(thrownBy(() -> bookmarkService.create(ME, new BookmarkCreateRequest(BookmarkTargetType.USER, TARGET)))
                .getErrorCode()).isEqualTo(ErrorCode.BOOKMARK_DUPLICATED);
    }

    @Test
    @DisplayName("UNIQUE가 아닌 다른 무결성 오류는 중복으로 둔갑시키지 않고 그대로 던진다")
    void create_otherIntegrityErrorIsNotMasked() {
        given(userService.getProfileSummaries(List.of(TARGET))).willReturn(Map.of(TARGET, profile(TARGET)));
        given(bookmarkRepository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("other",
                new RuntimeException("Data too long for column 'x'")));

        assertThat(catchThrowable(() -> bookmarkService.create(ME,
                new BookmarkCreateRequest(BookmarkTargetType.USER, TARGET))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("목록은 클라이언트 sort를 무시하고 최신순으로 조회하며, USER 대상은 한 번에 일괄 조회해 target을 채운다")
    void list_fixedSort_andBatchesUserLookup() {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        Page<Bookmark> page = new PageImpl<>(List.of(userBookmark(21L, 5L), userBookmark(20L, 6L)),
                PageRequest.of(0, 20), 2);
        given(bookmarkRepository.findByUserId(eq(ME), pageable.capture())).willReturn(page);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        given(userService.getProfileSummaries(ids.capture())).willReturn(Map.of(5L, profile(5L), 6L, profile(6L)));

        PageResponse<BookmarkResponse> response = bookmarkService.getBookmarks(
                ME, null, PageRequest.of(0, 20, Sort.by("bogus")));

        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        assertThat(ids.getValue()).containsExactlyInAnyOrder(5L, 6L);
        assertThat(response.content()).extracting(BookmarkResponse::id).containsExactly(21L, 20L);
        assertThat(response.content()).allSatisfy(item -> assertThat(item.target()).isNotNull());
        verify(userService).getProfileSummaries(any());
    }

    @Test
    @DisplayName("대상이 탈퇴·삭제돼 조회되지 않으면 목록에서 빼지 않고 target만 null이다")
    void list_missingTargetKeepsRowWithNullTarget() {
        Page<Bookmark> page = new PageImpl<>(List.of(userBookmark(21L, 5L), userBookmark(20L, 6L)),
                PageRequest.of(0, 20), 2);
        given(bookmarkRepository.findByUserId(eq(ME), any())).willReturn(page);
        given(userService.getProfileSummaries(any())).willReturn(Map.of(6L, profile(6L)));

        PageResponse<BookmarkResponse> response = bookmarkService.getBookmarks(ME, null, PageRequest.of(0, 20));

        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.content().get(0).id()).isEqualTo(21L);
        assertThat(response.content().get(0).target()).isNull();
        assertThat(response.content().get(0).targetId()).isEqualTo(5L);
        assertThat(response.content().get(1).target()).isNotNull();
    }

    @Test
    @DisplayName("targetType 필터는 해당 타입의 조회로 나뉜다")
    void list_dispatchesByTargetType() {
        Page<Bookmark> empty = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        given(bookmarkRepository.findByUserIdAndTargetTeamIdIsNotNull(eq(ME), any())).willReturn(empty);
        given(bookmarkRepository.findByUserIdAndTargetUserIdIsNotNull(eq(ME), any())).willReturn(empty);
        given(bookmarkRepository.findByUserIdAndTargetSongIdIsNotNull(eq(ME), any())).willReturn(empty);

        bookmarkService.getBookmarks(ME, BookmarkTargetType.TEAM, PageRequest.of(0, 20));
        bookmarkService.getBookmarks(ME, BookmarkTargetType.USER, PageRequest.of(0, 20));
        bookmarkService.getBookmarks(ME, BookmarkTargetType.SONG, PageRequest.of(0, 20));

        verify(bookmarkRepository).findByUserIdAndTargetTeamIdIsNotNull(eq(ME), any());
        verify(bookmarkRepository).findByUserIdAndTargetUserIdIsNotNull(eq(ME), any());
        verify(bookmarkRepository).findByUserIdAndTargetSongIdIsNotNull(eq(ME), any());
        verify(bookmarkRepository, never()).findByUserId(any(), any());
    }

    @Test
    @DisplayName("본인의 저장을 삭제하면 id와 deleted=true를 돌려준다")
    void delete_own() {
        Bookmark bookmark = userBookmark(21L, TARGET);
        given(bookmarkRepository.findByIdAndUserId(21L, ME)).willReturn(Optional.of(bookmark));

        DeleteResponse response = bookmarkService.delete(ME, 21L);

        assertThat(response).isEqualTo(new DeleteResponse(21L, true));
        verify(bookmarkRepository).delete(bookmark);
    }

    @Test
    @DisplayName("없는 저장이거나 남의 저장이면 똑같이 BOOKMARK_NOT_FOUND다 (존재 여부를 드러내지 않는다)")
    void delete_missingOrOthers() {
        given(bookmarkRepository.findByIdAndUserId(21L, ME)).willReturn(Optional.empty());

        assertThat(thrownBy(() -> bookmarkService.delete(ME, 21L)).getErrorCode())
                .isEqualTo(ErrorCode.BOOKMARK_NOT_FOUND);
        verify(bookmarkRepository, never()).delete(any());
    }
}
