package com.moyeorock.domain.song.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SongDifficultyConverterTest {

    private final SongDifficultyConverter converter = new SongDifficultyConverter();

    @Test
    @DisplayName("DB 컬럼이 NULL이면 예외 없이 null을 돌려준다 (difficulty 미입력 곡)")
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
        Map<Instrument, Level> difficulty = Map.of(Instrument.VOCAL, Level.ADVANCED);

        String json = converter.convertToDatabaseColumn(difficulty);

        assertThat(converter.convertToEntityAttribute(json)).containsEntry(Instrument.VOCAL, Level.ADVANCED);
    }
}
