package com.moyeorock.domain.song.client;

import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * AI 서버 곡 카탈로그 한 페이지({@code GET {ai.server.base-url}/v1/songs?cursor=&limit=}, 이슈 #147).
 * {@code nextCursor}가 null이면 마지막 페이지다. 호출 형식은 AI 팀과 맞추기 전 잠정안이다(api-spec.md §12).
 *
 * <p>항목 검증은 SongService가 곡마다 한다 — 검증에 걸린 곡만 건너뛰고 나머지는 저장한다. 글자 수 상한은 songs 컬럼 길이다(erd.md §8).
 */
public record AiSongPage(
        List<Item> songs,
        String nextCursor
) {

    // 비어 있거나, 공백이 아닌 글자로 시작하고 끝난다
    private static final String NO_SURROUNDING_SPACE = "(\\S(.*\\S)?)?";

    public record Item(
            // 대소문자를 구분하고(V13), 앞뒤 공백은 받지 않는다 — 공백 하나 차이로 같은 곡이 따로 생기지 않게
            @NotBlank(message = "외부 곡 아이디는 필수입니다.")
            @Size(max = 100, message = "외부 곡 아이디는 100자 이하여야 합니다.")
            @Pattern(regexp = NO_SURROUNDING_SPACE, message = "외부 곡 아이디 앞뒤에 공백이 있으면 안 됩니다.")
            String externalId,

            @NotBlank(message = "곡 제목은 필수입니다.")
            @Size(max = 200, message = "곡 제목은 200자 이하여야 합니다.")
            String title,

            @NotBlank(message = "아티스트는 필수입니다.")
            @Size(max = 100, message = "아티스트는 100자 이하여야 합니다.")
            String artist,

            @NotNull(message = "장르는 필수입니다.")
            Genre genre,

            // 빈 문자열·공백뿐이면 키 정보 없음(null)으로 저장한다(SongService)
            @Size(max = 10, message = "곡 키는 10자 이하여야 합니다.")
            String songKey,

            @Positive(message = "BPM은 양수여야 합니다.")
            Integer bpm,

            // 곡 검색 응답(SongDifficultyResponse)과 같은 배열 모양. 없거나 비어 있으면 난이도 정보 없음이다.
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
