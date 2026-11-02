package com.moyeorock.domain.bookmark.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.bookmark.entity.Bookmark;
import com.moyeorock.domain.bookmark.enums.BookmarkTargetType;
import com.moyeorock.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** 대상 타입별 조회·UNIQUE 의미·hard delete를 실제 MySQL에서 확인한다. */
@DataJpaTest
@Import({JpaAuditingConfig.class, TestcontainersConfig.class})
class BookmarkRepositoryTest {

    private static final Long ME = 1L;
    private static final Long OTHER = 2L;
    private static final Sort LATEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Autowired
    private EntityManager entityManager;

    private Bookmark saveUser(Long userId, Long targetUserId) {
        return bookmarkRepository.saveAndFlush(Bookmark.create(userId, null, targetUserId, null));
    }

    @Test
    @DisplayName("목록은 본인 저장만 최신순(createdAt → id 내림차순)으로 페이징해 돌려준다")
    void findByUserId_ownOnly_latestFirst() {
        Bookmark first = saveUser(ME, 10L);
        Bookmark second = saveUser(ME, 11L);
        Bookmark third = saveUser(ME, 12L);
        saveUser(OTHER, 10L);

        Page<Bookmark> page = bookmarkRepository.findByUserId(ME, PageRequest.of(0, 2, LATEST_FIRST));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(Bookmark::getId).containsExactly(third.getId(), second.getId());
        assertThat(bookmarkRepository.findByUserId(ME, PageRequest.of(1, 2, LATEST_FIRST)).getContent())
                .extracting(Bookmark::getId).containsExactly(first.getId());
    }

    @Test
    @DisplayName("대상 타입 필터는 exclusive-arc 컬럼으로 TEAM·USER·SONG을 나눈다")
    void filterByTargetType() {
        Bookmark team = bookmarkRepository.saveAndFlush(Bookmark.create(ME, 100L, null, null));
        Bookmark user = bookmarkRepository.saveAndFlush(Bookmark.create(ME, null, 100L, null));
        Bookmark song = bookmarkRepository.saveAndFlush(Bookmark.create(ME, null, null, 100L));
        PageRequest pageable = PageRequest.of(0, 20, LATEST_FIRST);

        assertThat(bookmarkRepository.findByUserIdAndTargetTeamIdIsNotNull(ME, pageable).getContent())
                .extracting(Bookmark::getId).containsExactly(team.getId());
        assertThat(bookmarkRepository.findByUserIdAndTargetUserIdIsNotNull(ME, pageable).getContent())
                .extracting(Bookmark::getId).containsExactly(user.getId());
        assertThat(bookmarkRepository.findByUserIdAndTargetSongIdIsNotNull(ME, pageable).getContent())
                .extracting(Bookmark::getId).containsExactly(song.getId());
        assertThat(user.getTargetType()).isEqualTo(BookmarkTargetType.USER);
    }

    @Test
    @DisplayName("같은 사용자가 같은 사용자를 두 번 저장하면 대상별 UNIQUE 인덱스(uk_bookmarks_...)에 걸린다")
    void duplicate_violatesUnique() {
        saveUser(ME, 10L);

        assertThatThrownBy(() -> saveUser(ME, 10L))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(((DataIntegrityViolationException) e).getMostSpecificCause().getMessage())
                        .contains("uk_bookmarks_"));
    }

    @Test
    @DisplayName("UNIQUE는 대상 타입별로 중복만 막는다 — 같은 번호라도 타입이 다르거나 저장한 사람이 다르면 허용된다")
    void unique_isPerTargetType() {
        saveUser(ME, 10L);

        bookmarkRepository.saveAndFlush(Bookmark.create(ME, 10L, null, null));
        bookmarkRepository.saveAndFlush(Bookmark.create(ME, null, null, 10L));
        saveUser(OTHER, 10L);

        assertThat(bookmarkRepository.findByUserId(ME, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("사용자 대상 중복 여부 확인은 본인 저장만 본다")
    void existsByUserIdAndTargetUserId() {
        saveUser(ME, 10L);

        assertThat(bookmarkRepository.existsByUserIdAndTargetUserId(ME, 10L)).isTrue();
        assertThat(bookmarkRepository.existsByUserIdAndTargetUserId(ME, 11L)).isFalse();
        assertThat(bookmarkRepository.existsByUserIdAndTargetUserId(OTHER, 10L)).isFalse();
    }

    @Test
    @DisplayName("본인 저장만 조회되고 남의 저장 id는 비어 있다")
    void findByIdAndUserId() {
        Bookmark mine = saveUser(ME, 10L);

        assertThat(bookmarkRepository.findByIdAndUserId(mine.getId(), ME)).isPresent();
        assertThat(bookmarkRepository.findByIdAndUserId(mine.getId(), OTHER)).isEmpty();
    }

    @Test
    @DisplayName("삭제하면 행이 실제로 사라진다 (hard delete)")
    void delete_removesRow() {
        Bookmark saved = saveUser(ME, 10L);

        bookmarkRepository.delete(saved);
        entityManager.flush();
        entityManager.clear();

        assertThat(bookmarkRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    @DisplayName("대상이 0개이거나 2개 이상이면 만들 수 없다 (exclusive-arc 검증)")
    void create_requiresExactlyOneTarget() {
        assertThatThrownBy(() -> Bookmark.create(ME, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Bookmark.create(ME, 1L, 2L, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
