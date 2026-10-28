package com.moyeorock.domain.song.entity;

import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

// songs.difficulty JSON 컬럼 <-> Map<Instrument, Level> 매핑 (dto-naming.md §10, conventions.md §2).
@Converter
public class SongDifficultyConverter implements AttributeConverter<Map<Instrument, Level>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<Instrument, Level>> DIFFICULTY_TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(Map<Instrument, Level> attribute) {
        return attribute == null ? null : OBJECT_MAPPER.writeValueAsString(attribute);
    }

    @Override
    public Map<Instrument, Level> convertToEntityAttribute(String dbData) {
        // difficulty는 nullable이다(erd.md §8) — Jackson readValue(null)은 예외를 던진다.
        return dbData == null ? null : OBJECT_MAPPER.readValue(dbData, DIFFICULTY_TYPE);
    }
}
