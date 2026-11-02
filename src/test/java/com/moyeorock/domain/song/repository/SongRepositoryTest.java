package com.moyeorock.domain.song.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.song.entity.Song;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.config.JpaAuditingConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/** 곡 검색의 제목·아티스트 부분 일치, LIKE 이스케이프, 장르 필터, 정렬·페이징을 실제 MySQL에서 확인한다. */
@DataJpaTest
@Import({JpaAuditingConfig.class, TestcontainersConfig.class})
class SongRepositoryTest {

    @Autowired
    private SongRepository songRepository;

    @Test
    @DisplayName("keyword는 제목이나 아티스트 중 하나에 포함되면 걸리고, 결과는 제목 순이다")
    void search_matchesTitleOrArtist_orderedByTitle() {
        save("Queen of Hearts", "Juice Newton", Genre.POP);
        save("Bohemian Rhapsody", "Queen", Genre.ROCK);
        save("Creep", "Radiohead", Genre.ROCK);

        Page<Song> page = songRepository.search("Queen", null, PageRequest.of(0, 20));

        assertThat(titles(page)).containsExactly("Bohemian Rhapsody", "Queen of Hearts");
    }

    @Test
    @DisplayName("genre를 주면 그 장르만, keyword와 함께 주면 둘 다 맞는 곡만 나온다")
    void search_filtersByGenre_andCombinesWithKeyword() {
        save("Bohemian Rhapsody", "Queen", Genre.ROCK);
        save("Queen of Hearts", "Juice Newton", Genre.POP);
        save("Creep", "Radiohead", Genre.ROCK);

        assertThat(titles(songRepository.search(null, Genre.ROCK, PageRequest.of(0, 20))))
                .containsExactly("Bohemian Rhapsody", "Creep");
        assertThat(titles(songRepository.search("Queen", Genre.POP, PageRequest.of(0, 20))))
                .containsExactly("Queen of Hearts");
    }

    @Test
    @DisplayName("keyword의 %·_는 와일드카드가 아니라 글자 그대로 찾는다")
    void search_treatsLikeWildcardsLiterally() {
        save("100% Pure", "A", Genre.POP);
        save("100 Pure", "B", Genre.POP);
        save("a_b", "C", Genre.POP);
        save("axb", "D", Genre.POP);

        assertThat(titles(songRepository.search("100%", null, PageRequest.of(0, 20)))).containsExactly("100% Pure");
        assertThat(titles(songRepository.search("a_b", null, PageRequest.of(0, 20)))).containsExactly("a_b");
    }

    @Test
    @DisplayName("제목이 같으면 먼저 저장한 곡이 먼저이고, 페이지를 나눠도 전체 개수가 맞다")
    void search_ordersByTitleThenId_andCountsAcrossPages() {
        Song first = save("Same", "First", Genre.ROCK);
        Song second = save("Same", "Second", Genre.ROCK);
        save("Another", "Third", Genre.ROCK);

        Page<Song> firstPage = songRepository.search(null, null, PageRequest.of(0, 2));
        Page<Song> secondPage = songRepository.search(null, null, PageRequest.of(1, 2));

        assertThat(firstPage.getContent()).extracting(Song::getArtist).containsExactly("Third", "First");
        assertThat(secondPage.getContent()).extracting(Song::getId).containsExactly(second.getId());
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(first.getId()).isLessThan(second.getId());
    }

    private Song save(String title, String artist, Genre genre) {
        return songRepository.save(Song.create(title, artist, genre, null, null, null, null));
    }

    private static List<String> titles(Page<Song> page) {
        return page.getContent().stream().map(Song::getTitle).toList();
    }
}
