package com.moyeorock.domain.song.dto.response;

/**
 * AI 서버 곡 일괄 등록·갱신 결과. {@code created}는 새로 만든 곡 수, {@code updated}는 같은 externalId가 이미 있어
 * 보낸 값으로 바꾼 곡 수다(내용이 같아도 갱신으로 센다). 둘을 더하면 요청한 곡 수다.
 */
public record SongsUpsertResponse(int created, int updated) {
}
