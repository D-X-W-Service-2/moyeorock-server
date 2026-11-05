package com.moyeorock.domain.song.service;

import com.moyeorock.domain.song.dto.request.SongsUpsertRequest;
import com.moyeorock.domain.song.dto.response.SongResponse;
import com.moyeorock.domain.song.dto.response.SongSummaryResponse;
import com.moyeorock.domain.song.dto.response.SongsUpsertResponse;
import com.moyeorock.domain.song.entity.Song;
import com.moyeorock.domain.song.repository.SongRepository;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 곡 마스터 조회와 AI 서버의 곡 일괄 등록·갱신. 곡은 AI 서버 카탈로그에서 들어오는 데이터라 화면용 쓰기는 없다(Song 엔티티 주석).
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

    /**
     * AI 서버 곡 일괄 등록·갱신(PUT /v1/internal/songs, 이슈 #147). externalId가 이미 있으면 보낸 값으로 바꾸고, 없으면
     * 새로 만든다 — 같은 요청을 다시 보내도 결과가 같다. 요청에 없는 곡은 지우지 않는다(팀 선택곡·셋리스트가 참조한다).
     * 한 요청이 한 트랜잭션이라 하나라도 틀리면 아무것도 저장되지 않는다. 한 요청 안의 externalId 중복과 한 곡의 같은 파트
     * 중복은 VALIDATION_FAILED이고, 메시지에 몇 번째 곡인지 담는다(검증 메시지와 같은 {@code songs[i].필드: 사유} 모양).
     *
     * <p>같은 새 externalId를 담은 요청 둘이 동시에 오면 유니크 제약(uk_songs_external_id)으로 한쪽이 실패한다 — AI 서버가
     * 요청을 차례로 보내는 것을 전제로 한다(api-spec.md §9).
     */
    @Transactional
    public SongsUpsertResponse upsertAll(SongsUpsertRequest request) {
        List<SongsUpsertRequest.Item> items = request.songs();
        ensureUniqueExternalIds(items);
        Map<String, Song> existing = songRepository.findAllByExternalIdIn(
                        items.stream().map(SongsUpsertRequest.Item::externalId).toList()).stream()
                .collect(Collectors.toMap(Song::getExternalId, Function.identity()));

        List<Song> created = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            SongsUpsertRequest.Item item = items.get(i);
            Map<Instrument, Level> difficulty = toDifficulty(i, item.difficulty());
            Song song = existing.get(item.externalId());
            if (song == null) {
                created.add(Song.create(item.title(), item.artist(), item.genre(), item.songKey(), item.bpm(),
                        difficulty, item.externalId()));
            } else {
                song.update(item.title(), item.artist(), item.genre(), item.songKey(), item.bpm(), difficulty);
            }
        }
        songRepository.saveAll(created);
        return new SongsUpsertResponse(created.size(), items.size() - created.size());
    }

    private static void ensureUniqueExternalIds(List<SongsUpsertRequest.Item> items) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            if (!seen.add(items.get(i).externalId())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "songs[" + i + "].externalId: 같은 요청 안에 같은 외부 곡 아이디가 있습니다.");
            }
        }
    }

    // 요청의 배열을 DB 모양(파트 → 수준 객체, songs.difficulty JSON)으로 바꾼다. 없거나 비어 있으면 난이도 정보 없음(null)이다.
    private static Map<Instrument, Level> toDifficulty(int index, List<SongsUpsertRequest.Difficulty> difficulty) {
        if (difficulty == null || difficulty.isEmpty()) {
            return null;
        }
        Map<Instrument, Level> result = new EnumMap<>(Instrument.class);
        for (SongsUpsertRequest.Difficulty part : difficulty) {
            if (result.put(part.part(), part.level()) != null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "songs[" + index + "].difficulty: 같은 파트가 두 번 들어 있습니다.");
            }
        }
        return result;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.strip();
    }
}
