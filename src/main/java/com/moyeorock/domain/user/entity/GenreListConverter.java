package com.moyeorock.domain.user.entity;

import com.moyeorock.global.common.enums.Genre;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * users.genres(JSON) ↔ List<Genre>.
 * 값이 enum 이름뿐인 문자열 배열이라(["ROCK", "INDIE"]) JSON 라이브러리 없이 직접 직렬화한다.
 * conventions.md §2 "JSON 컬럼은 컨버터로 매핑".
 */
@Converter
public class GenreListConverter implements AttributeConverter<List<Genre>, String> {

    @Override
    public String convertToDatabaseColumn(List<Genre> genres) {
        if (genres == null) {
            return null;
        }
        return genres.stream()
                .map(genre -> "\"" + genre.name() + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }

    @Override
    public List<Genre> convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        String body = dbData.trim();
        if (body.length() < 2 || body.charAt(0) != '[' || body.charAt(body.length() - 1) != ']') {
            throw new IllegalArgumentException("users.genres 형식이 JSON 배열이 아닙니다: " + dbData);
        }
        body = body.substring(1, body.length() - 1).trim();
        if (body.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(body.split(","))
                .map(token -> token.trim().replace("\"", ""))
                .map(Genre::valueOf)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    static List<Genre> emptyIfNull(List<Genre> genres) {
        return genres == null ? Collections.emptyList() : genres;
    }
}
