package com.moyeorock.domain.song.client;

import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import java.util.List;

/**
 * AI 곡 추천 요청 본문({@code POST {ai.recommend.base-url}/v1/recommendations/songs}, 이슈 #171).
 * 후보 곡은 보내지 않는다. AI가 자기 카탈로그에서 골라 {@code externalId}로 돌려준다.
 *
 * <p>{@code mode}는 {@code "TEAM"} 또는 {@code "PERSONAL"}이다. {@code members}는 세션과 그 세션의 실력이고, 실력을 모르는
 * 사람은 넣지 않는다(AI가 값이 비면 422를 준다). {@code limit}은 1~100이다.
 */
public record AiSongRecommendRequest(
        String mode,
        List<Genre> genres,
        List<Member> members,
        int limit
) {

    public static final String MODE_TEAM = "TEAM";
    public static final String MODE_PERSONAL = "PERSONAL";

    public static AiSongRecommendRequest forTeam(List<Genre> genres, List<Member> members, int limit) {
        return new AiSongRecommendRequest(MODE_TEAM, genres, members, limit);
    }

    public static AiSongRecommendRequest forPersonal(List<Genre> genres, List<Member> members, int limit) {
        return new AiSongRecommendRequest(MODE_PERSONAL, genres, members, limit);
    }

    /** 세션 하나. 팀 추천이면 팀원이 이 팀에서 맡은 세션과 그 악기의 실력, 개인 추천이면 내 악기별 실력이다. */
    public record Member(Instrument part, Level level) {
    }
}
