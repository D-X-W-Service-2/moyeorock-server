package com.moyeorock.domain.bookmark.repository;

import com.moyeorock.domain.bookmark.entity.Bookmark;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    Page<Bookmark> findByUserId(Long userId, Pageable pageable);

    // 대상 타입 필터. 대상 타입은 exclusive-arc 컬럼 중 어느 것이 채워졌는지로 정해진다(erd.md §3).
    Page<Bookmark> findByUserIdAndTargetTeamIdIsNotNull(Long userId, Pageable pageable);

    Page<Bookmark> findByUserIdAndTargetUserIdIsNotNull(Long userId, Pageable pageable);

    Page<Bookmark> findByUserIdAndTargetSongIdIsNotNull(Long userId, Pageable pageable);

    boolean existsByUserIdAndTargetUserId(Long userId, Long targetUserId);

    /** 본인의 저장만 조회한다. 남의 저장 id는 없는 것과 똑같이 취급해 존재 여부를 드러내지 않는다. */
    Optional<Bookmark> findByIdAndUserId(Long id, Long userId);
}
