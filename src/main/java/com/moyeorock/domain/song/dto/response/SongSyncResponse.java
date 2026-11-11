package com.moyeorock.domain.song.dto.response;

/**
 * 곡 수동 동기화 결과(이슈 #162). AI 카탈로그를 처음부터 끝까지 한 번 받은 결과다.
 * {@code skipped}는 검증에 걸려 건너뛴 곡 수다(이유는 서버 로그).
 */
public record SongSyncResponse(
        int pages,
        int created,
        int updated,
        int skipped
) {
}
