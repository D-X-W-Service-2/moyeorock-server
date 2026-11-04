package com.moyeorock.domain.song.service;

import com.moyeorock.domain.song.dto.response.SongResponse;
import com.moyeorock.domain.song.dto.response.SongSummaryResponse;
import com.moyeorock.domain.song.repository.SongRepository;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.common.enums.Genre;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * 곡 마스터 조회. 곡은 외부 카탈로그에서 들여오는 데이터라 이 Service는 쓰지 않고 읽기만 한다(Song 엔티티 주석).
 * 다른 Service를 부르지 않는다 — setlist가 곡 존재·요약을 확인하려고 이 Service를 부른다.
 */
@Service
@RequiredArgsConstructor
public class SongService {

    private final SongRepository songRepository;

    /**
     * 곡 검색. keyword는 앞뒤 공백을 지우고, 비어 있으면 조건 없이 전체다. 정렬은 제목 순 고정(SongRepository)이라
     * 클라이언트 sort는 버린다 — 엔티티에 없는 속성이 들어오면 500이 난다. 단일 쿼리라 트랜잭션을 걸지 않는다.
     */
    public PageResponse<SongResponse> search(String keyword, Genre genre, Pageable pageable) {
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return PageResponse.from(songRepository.search(normalizeKeyword(keyword), genre, unsorted)
                .map(SongResponse::from));
    }

    /** 곡 축약형 일괄 조회(선택곡·셋리스트 응답용). 없는 id는 결과에서 빠진다 — 존재 확인에도 쓴다. */
    public Map<Long, SongSummaryResponse> getSummaries(Collection<Long> songIds) {
        if (songIds == null || songIds.isEmpty()) {
            return Map.of();
        }
        return songRepository.findAllById(songIds).stream()
                .map(SongSummaryResponse::from)
                .collect(Collectors.toMap(SongSummaryResponse::id, Function.identity()));
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.strip();
    }
}
