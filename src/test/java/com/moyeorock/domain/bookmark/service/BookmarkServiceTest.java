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
import com.moyeorock.domain.team.dto.response.TeamSummaryResponse;
import com.moyeorock.domain.team.enums.TeamStatus;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.domain.user.dto.response.UserProfileSummaryResponse;
import com.moyeorock.domain.user.service.UserService;
import com.moyeorock.domain.song.dto.response.SongSummaryResponse;
import com.moyeorock.domain.song.service.SongService;
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

    @Mock
    private TeamService teamService;

    @Mock
    private SongService songService;

    @InjectMocks
    private BookmarkService bookmarkService;

    private static UserProfileSummaryResponse profile(Long id) {
        return new UserProfileSummaryResponse(id, "민서", Region.SEOUL, List.of(Genre.JAZZ), "https://img/c.jpg");
    }

    private static TeamSummaryResponse teamSummary(Long id) {
        return new TeamSummaryResponse(id, "무명밴드", Region.SEOUL, List.of(Genre.ROCK), TeamStatus.ACTIVE, 3);
    }

    private static Bookmark teamBookmark(Long id, Long targetTeamId) {
        Bookmark bookmark = Bookmark.create(ME, targetTeamId, null, null);
        ReflectionTestUtils.setField(bookmark, "id", id);
        ReflectionTestUtils.setField(bookmark, "createdAt", LocalDateTime.of(2026, 8, 10, 12, 0));
        return bookmark;
    }

    private static Bookmark userBookmark(Long id, Long targetUserId) {
        Bookmark bookmark = Bookmark.create(ME, null, targetUserId, null);
        ReflectionTestUtils.setField(bookmark, "id", id);
        ReflectionTestUtils.setField(bookmark, "createdAt", LocalDateTime.of(2026, 8, 10, 12, 0));
        return bookmark;
    }

    private static Bookmark songBookmark(Long id, Long targetSongId) {
        Bookmark bookmark = Bookmark.create(ME, null, null, targetSongId);
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
    @DisplayName("자기 자신은 저장할 수 없다 — VALIDATION_FAILED이고 아무것도 호출하지 않는다")
    void create_selfIsRejected() {
        assertThat(thrownBy(() -> bookmarkService.create(ME, new BookmarkCreateRequest(BookmarkTargetType.USER, ME)))
                .getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);

        verifyNoInteractions(userService);
        verify(bookmarkRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("SONG 대상을 저장하면 곡 제목을 name으로 돌려주고 지역·장르·이미지는 null이다")
    void create_songTarget() {
        given(songService.getSummaries(List.of(TARGET)))
                .willReturn(Map.of(TARGET, new SongSummaryResponse(TARGET, "Creep", "Radiohead")));
        given(bookmarkRepository.existsByUserIdAndTargetSongId(ME, TARGET)).willReturn(false);
        given(bookmarkRepository.saveAndFlush(any())).willReturn(songBookmark(21L, TARGET));

        BookmarkResponse response = bookmarkService.create(ME, new BookmarkCreateRequest(BookmarkTargetType.SONG, TARGET));

        assertThat(response.targetType()).isEqualTo(BookmarkTargetType.SONG);
        assertThat(response.target().name()).isEqualTo("Creep");
        assertThat(response.target().region()).isNull();
        assertThat(response.target().genres()).isNull();
        ArgumentCaptor<Bookmark> saved = ArgumentCaptor.forClass(Bookmark.class);
        verify(bookmarkRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getTargetSongId()).isEqualTo(TARGET);
        verifyNoInteractions(userService, teamService);
    }

    @Test
    @DisplayName("없는 곡은 SONG_NOT_FOUND이고, 이미 저장한 곡은 BOOKMARK_DUPLICATED이며 저장하지 않는다")
    void create_songMissingOrDuplicated() {
        given(songService.getSummaries(List.of(TARGET))).willReturn(Map.of());
        assertThat(thrownBy(() -> bookmarkService.create(ME, new BookmarkCreateRequest(BookmarkTargetType.SONG, TARGET)))
                .getErrorCode()).isEqualTo(ErrorCode.SONG_NOT_FOUND);

        given(songService.getSummaries(List.of(TARGET)))
                .willReturn(Map.of(TARGET, new SongSummaryResponse(TARGET, "Creep", "Radiohead")));
        given(bookmarkRepository.existsByUserIdAndTargetSongId(ME, TARGET)).willReturn(true);
        assertThat(thrownBy(() -> bookmarkService.create(ME, new BookmarkCreateRequest(BookmarkTargetType.SONG, TARGET)))
                .getErrorCode()).isEqualTo(ErrorCode.BOOKMARK_DUPLICATED);
        verify(bookmarkRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("TEAM 대상을 저장하면 팀 요약(이름·지역·장르)과 함께 돌려주고 이미지는 null이다")
    void create_teamTarget() {
        given(teamService.getSummaries(List.of(TARGET))).willReturn(Map.of(TARGET, teamSummary(TARGET)));
        given(bookmarkRepository.existsByUserIdAndTargetTeamId(ME, TARGET)).willReturn(false);
        given(bookmarkRepository.saveAndFlush(any())).willReturn(teamBookmark(21L, TARGET));

        BookmarkResponse response = bookmarkService.create(ME, new BookmarkCreateRequest(BookmarkTargetType.TEAM, TARGET));

        assertThat(response.targetType()).isEqualTo(BookmarkTargetType.TEAM);
        assertThat(response.targetId()).isEqualTo(TARGET);
        assertThat(response.target().name()).isEqualTo("무명밴드");
        assertThat(response.target().region()).isEqualTo(Region.SEOUL);
        assertThat(response.target().genres()).containsExactly(Genre.ROCK);
        assertThat(response.target().imageUrl()).isNull();
        ArgumentCaptor<Bookmark> saved = ArgumentCaptor.forClass(Bookmark.class);
        verify(bookmarkRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getTargetTeamId()).isEqualTo(TARGET);
        assertThat(saved.getValue().getTargetUserId()).isNull();
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("없거나 해체된 팀은 TEAM_NOT_FOUND이고 저장하지 않는다")
    void create_teamMissing() {
        given(teamService.getSummaries(List.of(TARGET))).willReturn(Map.of());

        assertThat(thrownBy(() -> bookmarkService.create(ME, new BookmarkCreateRequest(BookmarkTargetType.TEAM, TARGET)))
                .getErrorCode()).isEqualTo(ErrorCode.TEAM_NOT_FOUND);
        verify(bookmarkRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("이미 저장한 팀이면 BOOKMARK_DUPLICATED(409)이고 저장하지 않는다")
    void create_teamDuplicated() {
        given(teamService.getSummaries(List.of(TARGET))).willReturn(Map.of(TARGET, teamSummary(TARGET)));
        given(bookmarkRepository.existsByUserIdAndTargetTeamId(ME, TARGET)).willReturn(true);

        assertThat(thrownBy(() -> bookmarkService.create(ME, new BookmarkCreateRequest(BookmarkTargetType.TEAM, TARGET)))
                .getErrorCode()).isEqualTo(ErrorCode.BOOKMARK_DUPLICATED);
        verify(bookmarkRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("목록은 USER와 TEAM 대상을 각각 한 번씩 일괄 조회하고, 해체된 팀은 target만 null이다")
    void list_batchesTeamLookup() {
        Page<Bookmark> page = new PageImpl<>(
                List.of(teamBookmark(23L, 8L), teamBookmark(22L, 9L), userBookmark(21L, 5L)), PageRequest.of(0, 20), 3);
        given(bookmarkRepository.findByUserId(eq(ME), any())).willReturn(page);
        given(userService.getProfileSummaries(any())).willReturn(Map.of(5L, profile(5L)));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> teamIds = ArgumentCaptor.forClass(Collection.class);
        given(teamService.getSummaries(teamIds.capture())).willReturn(Map.of(8L, teamSummary(8L)));

        PageResponse<BookmarkResponse> response = bookmarkService.getBookmarks(ME, null, PageRequest.of(0, 20));

        assertThat(teamIds.getValue()).containsExactlyInAnyOrder(8L, 9L);
        assertThat(response.content().get(0).target().name()).isEqualTo("무명밴드");
        assertThat(response.content().get(1).target()).isNull();
        assertThat(response.content().get(1).targetId()).isEqualTo(9L);
        assertThat(response.content().get(2).target().name()).isEqualTo("민서");
        verify(teamService).getSummaries(any());
        verify(userService).getProfileSummaries(any());
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

    @Test
    @DisplayName("목록의 SONG 대상은 한 번에 일괄 조회하고, 삭제된 곡은 target만 null이다")
    void list_batchesSongLookup() {
        Page<Bookmark> page = new PageImpl<>(
                List.of(songBookmark(23L, 8L), songBookmark(22L, 9L)), PageRequest.of(0, 20), 2);
        given(bookmarkRepository.findByUserId(eq(ME), any())).willReturn(page);
        given(songService.getSummaries(any()))
                .willReturn(Map.of(8L, new SongSummaryResponse(8L, "Creep", "Radiohead")));

        PageResponse<BookmarkResponse> response = bookmarkService.getBookmarks(ME, null, PageRequest.of(0, 20));

        assertThat(response.content().get(0).target().name()).isEqualTo("Creep");
        assertThat(response.content().get(1).target()).isNull();
        assertThat(response.content().get(1).targetId()).isEqualTo(9L);
        verify(songService).getSummaries(any());
    }
}
