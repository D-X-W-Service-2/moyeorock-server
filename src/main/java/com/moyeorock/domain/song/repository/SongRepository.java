package com.moyeorock.domain.song.repository;

import com.moyeorock.domain.song.entity.Song;
import com.moyeorock.global.common.enums.Genre;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SongRepository extends JpaRepository<Song, Long> {

    /**
     * 곡 검색. keyword는 제목·아티스트 부분 일치이고 {@code %}·{@code _}를 글자 그대로 찾는다(GroupRepository.findMyGroups와
     * 같은 방식 — SpEL {@code escape()}는 null 인자에서 NPE가 나서 null일 때는 부르지 않는다). genre가 null이면 전체.
     * 정렬은 제목 순 고정이고, 제목이 같으면 id 순으로 페이지 경계를 고정한다.
     */
    @Query("""
            SELECT s FROM Song s
            WHERE (:genre IS NULL OR s.genre = :genre)
              AND (:keyword IS NULL
                   OR s.title LIKE CONCAT('%', :#{#keyword == null ? null : escape(#keyword)}, '%')
                      ESCAPE :#{escapeCharacter()}
                   OR s.artist LIKE CONCAT('%', :#{#keyword == null ? null : escape(#keyword)}, '%')
                      ESCAPE :#{escapeCharacter()})
            ORDER BY s.title ASC, s.id ASC
            """)
    Page<Song> search(@Param("keyword") String keyword, @Param("genre") Genre genre, Pageable pageable);
}
