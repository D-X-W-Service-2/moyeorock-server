package com.moyeorock.domain.user.entity;

import com.moyeorock.global.common.enums.Genre;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

// users.genres JSON 컬럼 <-> List<Genre> 매핑 (conventions.md §2, recruit의 WantedSlotsConverter와
// 동일한 패턴). JPA가 컨버터를 리플렉션으로 직접 생성해 스프링 빈 주입을 못 받으므로 전용
// ObjectMapper를 둔다.
@Converter
public class UserGenresConverter implements AttributeConverter<List<Genre>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Genre>> GENRES_TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<Genre> attribute) {
        return attribute == null ? null : OBJECT_MAPPER.writeValueAsString(attribute);
    }

    @Override
    public List<Genre> convertToEntityAttribute(String dbData) {
        // users.genres는 온보딩 전까지 NULL이다 — Jackson readValue(null)은 예외를 던진다.
        return dbData == null ? null : OBJECT_MAPPER.readValue(dbData, GENRES_TYPE);
    }
}
