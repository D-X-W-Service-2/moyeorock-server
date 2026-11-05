package com.moyeorock.domain.song.dto.request;

import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * AI 서버 곡 일괄 등록·갱신(PUT /v1/internal/songs). {@code externalId}(AI 쪽 곡 id)가 이미 있으면 보낸 값으로 바꾸고,
 * 없으면 새로 만든다. 요청에 없는 곡은 지우지 않는다. 한 요청 안의 {@code externalId} 중복과 한 곡의 같은 파트 중복은
 * Service가 400으로 막는다. 글자 수 상한은 songs 컬럼 길이다(erd.md §8).
 */
public record SongsUpsertRequest(
        @NotEmpty(message = "곡 목록은 비어 있을 수 없습니다.")
        @Size(max = MAX_SONGS, message = "곡은 한 번에 " + MAX_SONGS + "개까지 보낼 수 있습니다.")
        List<@NotNull(message = "곡은 비어 있을 수 없습니다.") @Valid Item> songs
) {

    public static final int MAX_SONGS = 500;

    public record Item(
            @NotBlank(message = "외부 곡 아이디는 필수입니다.")
            @Size(max = 100, message = "외부 곡 아이디는 100자 이하여야 합니다.")
            String externalId,

            @NotBlank(message = "곡 제목은 필수입니다.")
            @Size(max = 200, message = "곡 제목은 200자 이하여야 합니다.")
            String title,

            @NotBlank(message = "아티스트는 필수입니다.")
            @Size(max = 100, message = "아티스트는 100자 이하여야 합니다.")
            String artist,

            @NotNull(message = "장르는 필수입니다.")
            Genre genre,

            @Size(max = 10, message = "곡 키는 10자 이하여야 합니다.")
            String songKey,

            @Positive(message = "BPM은 양수여야 합니다.")
            Integer bpm,

            // 곡 검색 응답(SongDifficultyResponse)과 같은 배열 모양으로 받는다. 없거나 비어 있으면 난이도 정보 없음이다.
            List<@NotNull(message = "난이도 항목은 비어 있을 수 없습니다.") @Valid Difficulty> difficulty
    ) {
    }

    public record Difficulty(
            @NotNull(message = "난이도 파트는 필수입니다.")
            Instrument part,

            @NotNull(message = "난이도 수준은 필수입니다.")
            Level level
    ) {
    }
}
