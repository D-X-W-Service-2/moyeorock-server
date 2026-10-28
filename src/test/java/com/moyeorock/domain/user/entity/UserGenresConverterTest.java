package com.moyeorock.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.moyeorock.global.common.enums.Genre;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserGenresConverterTest {

    private final UserGenresConverter converter = new UserGenresConverter();

    @Test
    @DisplayName("DB 컬럼이 NULL이면 예외 없이 null을 돌려준다 (온보딩 전 사용자 조회)")
    void convertToEntityAttribute_returnsNull_whenDbDataIsNull() {
        assertThatCode(() -> assertThat(converter.convertToEntityAttribute(null)).isNull())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("저장할 값이 null이면 예외 없이 null을 돌려준다")
    void convertToDatabaseColumn_returnsNull_whenAttributeIsNull() {
        assertThatCode(() -> assertThat(converter.convertToDatabaseColumn(null)).isNull())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("값이 있으면 그대로 JSON 왕복한다")
    void convertsNonNullValue_roundTrip() {
        List<Genre> genres = List.of(Genre.ROCK, Genre.INDIE);

        String json = converter.convertToDatabaseColumn(genres);

        assertThat(converter.convertToEntityAttribute(json)).containsExactly(Genre.ROCK, Genre.INDIE);
    }
}
