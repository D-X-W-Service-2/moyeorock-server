package com.moyeorock.domain.recruit.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

// wanted_slots JSON 컬럼 <-> List<WantedSlot> 매핑 (conventions.md §2 "JSON 컬럼은 컨버터로 매핑").
// JPA가 컨버터를 리플렉션으로 직접 생성하므로 스프링 빈 주입을 받을 수 없어, 이 클래스 전용
// ObjectMapper를 둔다.
@Converter
public class WantedSlotsConverter implements AttributeConverter<List<WantedSlot>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<WantedSlot>> WANTED_SLOTS_TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<WantedSlot> attribute) {
        return OBJECT_MAPPER.writeValueAsString(attribute);
    }

    @Override
    public List<WantedSlot> convertToEntityAttribute(String dbData) {
        return OBJECT_MAPPER.readValue(dbData, WANTED_SLOTS_TYPE);
    }
}
