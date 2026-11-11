package com.moyeorock.domain.team.client;

import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import com.moyeorock.global.common.enums.Region;
import java.util.List;

/**
 * AI 서버 팀원 추천 요청 본문({@code POST {ai.server.base-url}/v1/recommendations/members}, 이슈 #150).
 * 값은 ERD 컬럼과 우리 enum 그대로다. 호출 형식은 AI 팀과 맞추기 전 잠정안이다(api-spec.md §12).
 *
 * <p>{@code instrument}는 찾는 세션이고, {@code members}는 팀의 현재(ACTIVE) 팀원, {@code candidates}는
 * 백엔드가 고른 후보다. 온보딩 전 사용자나 세션 정보가 없는 팀원은 region·genres·level이 null일 수 있다.
 */
public record AiRecommendRequest(
        Team team,
        Instrument instrument,
        List<Member> members,
        List<Candidate> candidates
) {

    /** 추천받는 팀. {@code genres}는 team_genres의 장르다. */
    public record Team(Long id, Region region, List<Genre> genres) {
    }

    /** 팀의 현재 팀원. {@code instrument}는 이 팀에서 맡은 세션(team_members), {@code level}은 그 악기의 실력(user_instruments)이다. */
    public record Member(Long userId, Instrument instrument, Level level) {
    }

    /** 추천 후보. {@code level}은 요청한 악기에서의 실력이다. */
    public record Candidate(Long userId, Region region, List<Genre> genres, Level level) {
    }
}
