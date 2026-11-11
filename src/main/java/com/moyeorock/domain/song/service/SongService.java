package com.moyeorock.domain.song.service;

import com.moyeorock.domain.song.client.AiSongClient;
import com.moyeorock.domain.song.client.AiSongPage;
import com.moyeorock.domain.song.dto.response.SongResponse;
import com.moyeorock.domain.song.dto.response.SongSummaryResponse;
import com.moyeorock.domain.song.dto.response.SongSyncResponse;
import com.moyeorock.domain.song.entity.Song;
import com.moyeorock.domain.song.repository.SongRepository;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

/**
 * 곡 마스터 조회와 AI 서버 곡 카탈로그 동기화. 곡은 AI 서버 카탈로그에서 들어오는 데이터라 화면용 쓰기는 없다(Song 엔티티 주석).
 * 다른 Service를 부르지 않는다 — setlist가 곡 존재·요약을 확인하려고 이 Service를 부른다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SongService {

    // AI 서버에 요청하는 페이지 크기 — 한 페이지가 한 트랜잭션이다
    static final int SYNC_PAGE_SIZE = 500;

    private final SongRepository songRepository;
    private final AiSongClient aiSongClient;
    private final TransactionTemplate transactionTemplate;
    private final Validator validator;
    private final AtomicBoolean syncing = new AtomicBoolean(false);

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
     * AI 곡 동기화 스케줄({@code ai.song-sync.cron}, 기본 매일 04:00). {@code ai.song-sync.enabled=true}일 때만
     * 돈다(SongSyncConfig). 실패해도 다음 회차에 처음부터 다시 받으면 되므로 로그만 남긴다.
     */
    @Scheduled(cron = "${ai.song-sync.cron}", zone = "Asia/Seoul")
    public void syncFromAiOnSchedule() {
        try {
            SyncResult result = syncFromAi();
            log.info("AI 곡 동기화 완료: {}", result);
        } catch (RuntimeException e) {
            log.error("AI 곡 동기화 실패 — 이미 저장한 페이지는 남는다", e);
        }
    }

    /**
     * 수동 동기화(POST /v1/songs/sync, 이슈 #162). 스케줄과 같은 동기화를 바로 돌리고 결과를 돌려준다.
     * {@code ai.song-sync.enabled}(스케줄 켜기)와 무관하고, 로그인한 사용자면 누구나 부를 수 있다. 스케줄과 달리 돌지 못한
     * 이유를 에러로 알린다.
     *
     * <ul>
     *   <li>AI 서버 주소가 없음 → 503 SONG_SYNC_UNAVAILABLE</li>
     *   <li>이미 돌고 있음(스케줄·다른 수동 실행) → 409 SONG_SYNC_IN_PROGRESS</li>
     *   <li>AI 서버 호출 실패·응답 형식 위반 → 503 SONG_SYNC_UNAVAILABLE. 이미 저장한 앞 페이지는 남는다</li>
     * </ul>
     * 카탈로그 전체를 받을 때까지 응답을 기다린다 — 페이지마다 AI 응답을 최대 30초 기다린다(AiSongClient).
     */
    public SongSyncResponse syncFromAiManually(Long userId) {
        if (!aiSongClient.isConfigured()) {
            throw new BusinessException(ErrorCode.SONG_SYNC_UNAVAILABLE);
        }
        if (!syncing.compareAndSet(false, true)) {
            throw new BusinessException(ErrorCode.SONG_SYNC_IN_PROGRESS);
        }
        try {
            SyncResult result = syncAllPages();
            log.info("AI 곡 수동 동기화 완료: userId={}, {}", userId, result);
            return new SongSyncResponse(result.pages(), result.created(), result.updated(), result.skipped());
        } catch (RestClientException | IllegalStateException e) {
            // AI 서버 장애·시간 초과·응답 해석 실패(RestClientException)와 응답 형식 위반(IllegalStateException — songs 없음,
            // 같은 커서 반복). 자세한 원인은 로그에만 남긴다.
            log.error("AI 곡 수동 동기화 실패 — 이미 저장한 페이지는 남는다. userId={}", userId, e);
            throw new BusinessException(ErrorCode.SONG_SYNC_UNAVAILABLE);
        } finally {
            syncing.set(false);
        }
    }

    /**
     * AI 서버 곡 카탈로그를 처음부터 끝까지 받아 songs에 넣는다(이슈 #147). 백엔드가 AI 서버를 부르고, AI 서버는 백엔드를 부르지 않는다.
     *
     * <ul>
     *   <li>externalId 기준 upsert — 없으면 만들고, 있으면 받은 값으로 통째로 바꾼다(빠진 선택 값은 비워진다). 곡 id는 그대로다.
     *       다시 돌려도 결과가 같다. 카탈로그에 없는 곡은 지우지 않는다(팀 선택곡·셋리스트가 참조한다).</li>
     *   <li>HTTP 호출은 트랜잭션 밖에서 하고, 페이지마다 따로 커밋한다 — AI 응답을 기다리는 동안 DB 커넥션을 잡지 않고,
     *       중간에 실패해도 앞 페이지는 남는다. 그래서 이 메서드에는 {@code @Transactional}을 붙이지 않는다.</li>
     *   <li>검증에 걸린 곡(필수 값 누락·길이 초과·같은 파트 중복·한 페이지 안 externalId 중복)은 그 곡만 건너뛰고 로그를 남긴다.</li>
     *   <li>한 번에 하나만 돈다. 이미 돌고 있거나 AI 서버 주소가 없으면 아무것도 하지 않고 {@link SyncResult#NOT_RUN}이다.</li>
     *   <li>AI 서버 호출 실패와 응답 형식 위반(songs 없음, 같은 커서 반복)은 예외로 그 회차를 끝낸다.</li>
     * </ul>
     */
    public SyncResult syncFromAi() {
        if (!aiSongClient.isConfigured()) {
            log.warn("AI 서버 주소(ai.server.base-url)가 없어 곡 동기화를 건너뜁니다.");
            return SyncResult.NOT_RUN;
        }
        if (!syncing.compareAndSet(false, true)) {
            log.warn("AI 곡 동기화가 이미 돌고 있어 이번 실행은 건너뜁니다.");
            return SyncResult.NOT_RUN;
        }
        try {
            return syncAllPages();
        } finally {
            syncing.set(false);
        }
    }

    private SyncResult syncAllPages() {
        int pages = 0;
        int created = 0;
        int updated = 0;
        int skipped = 0;
        String cursor = null;
        do {
            AiSongPage page = aiSongClient.fetchSongs(cursor, SYNC_PAGE_SIZE);
            pages++;
            List<SongValues> valid = validSongs(pages, page.songs());
            skipped += page.songs().size() - valid.size();
            if (!valid.isEmpty()) {
                int createdInPage = transactionTemplate.execute(status -> upsert(valid));
                created += createdInPage;
                updated += valid.size() - createdInPage;
            }
            String next = page.nextCursor();
            if (next != null && next.equals(cursor)) {
                throw new IllegalStateException("AI 서버가 같은 커서를 다시 돌려줬습니다: " + next);
            }
            cursor = next;
        } while (cursor != null);
        return new SyncResult(pages, created, updated, skipped);
    }

    // 한 페이지를 한 트랜잭션으로 저장하고 새로 만든 곡 수를 돌려준다. 한 번에 하나만 돌아서 같은 새 externalId가 동시에 들어올 일은 없다.
    private int upsert(List<SongValues> songs) {
        Map<String, Song> existing = songRepository.findAllByExternalIdIn(
                        songs.stream().map(SongValues::externalId).toList()).stream()
                .collect(Collectors.toMap(Song::getExternalId, Function.identity()));
        List<Song> created = new ArrayList<>();
        for (SongValues values : songs) {
            Song song = existing.get(values.externalId());
            if (song == null) {
                created.add(Song.create(values.title(), values.artist(), values.genre(), values.songKey(),
                        values.bpm(), values.difficulty(), values.externalId()));
            } else {
                song.update(values.title(), values.artist(), values.genre(), values.songKey(), values.bpm(),
                        values.difficulty());
            }
        }
        songRepository.saveAll(created);
        return created.size();
    }

    // 곡마다 검증하고 DB 모양으로 바꾼다. 걸린 곡은 빼고 이유를 남긴다. 순서는 받은 순서다.
    private List<SongValues> validSongs(int pageNumber, List<AiSongPage.Item> items) {
        List<SongValues> result = new ArrayList<>(items.size());
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            AiSongPage.Item item = items.get(i);
            String problem = findProblem(item, seen);
            if (problem != null) {
                log.warn("AI 곡 건너뜀 — {}페이지 songs[{}] externalId={}: {}", pageNumber, i,
                        item == null ? null : item.externalId(), problem);
                continue;
            }
            seen.add(item.externalId());
            result.add(new SongValues(item.externalId(), item.title(), item.artist(), item.genre(),
                    toSongKey(item.songKey()), item.bpm(), toDifficulty(item.difficulty())));
        }
        return result;
    }

    private String findProblem(AiSongPage.Item item, Set<String> seen) {
        if (item == null) {
            return "곡이 비어 있습니다.";
        }
        Set<ConstraintViolation<AiSongPage.Item>> violations = validator.validate(item);
        if (!violations.isEmpty()) {
            return violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
        }
        if (seen.contains(item.externalId())) {
            return "같은 페이지에 같은 외부 곡 아이디가 이미 있습니다.";
        }
        if (hasDuplicatePart(item.difficulty())) {
            return "difficulty: 같은 파트가 두 번 들어 있습니다.";
        }
        return null;
    }

    private static boolean hasDuplicatePart(List<AiSongPage.Difficulty> difficulty) {
        if (difficulty == null) {
            return false;
        }
        Set<Instrument> parts = EnumSet.noneOf(Instrument.class);
        return !difficulty.stream().allMatch(part -> parts.add(part.part()));
    }

    // 받은 배열을 DB 모양(파트 → 수준 객체, songs.difficulty JSON)으로 바꾼다. 없거나 비어 있으면 난이도 정보 없음(null)이다.
    private static Map<Instrument, Level> toDifficulty(List<AiSongPage.Difficulty> difficulty) {
        if (difficulty == null || difficulty.isEmpty()) {
            return null;
        }
        Map<Instrument, Level> result = new EnumMap<>(Instrument.class);
        difficulty.forEach(part -> result.put(part.part(), part.level()));
        return result;
    }

    // 빈 문자열·공백뿐인 키는 정보 없음(null)이다 — 응답에 "" 가 나가지 않게 한다.
    private static String toSongKey(String songKey) {
        if (songKey == null || songKey.isBlank()) {
            return null;
        }
        return songKey.strip();
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.strip();
    }

    /** 동기화 한 회차 결과. 페이지 수, 새로 만든 곡, 받은 값으로 바꾼 곡(내용이 같아도 센다), 검증에 걸려 건너뛴 곡 수. */
    public record SyncResult(int pages, int created, int updated, int skipped) {

        /** AI 서버 주소가 없거나 이미 돌고 있어 이번 회차를 돌리지 않았다. */
        public static final SyncResult NOT_RUN = new SyncResult(0, 0, 0, 0);
    }

    // 검증을 통과하고 DB 모양으로 바꾼 곡 값
    private record SongValues(String externalId, String title, String artist, Genre genre, String songKey,
            Integer bpm, Map<Instrument, Level> difficulty) {
    }
}
