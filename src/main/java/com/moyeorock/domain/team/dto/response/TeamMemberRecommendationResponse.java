package com.moyeorock.domain.team.dto.response;

import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import com.moyeorock.global.common.enums.Instrument;
import java.util.List;

/**
 * 팀원 추천 결과(GET /v1/teams/{teamId}/members/recommendations). 점수가 높은 순이고 최대 10명이다.
 * 점수·근거는 AI 서버가 매긴 값 그대로다(dto-spec.md TeamMemberRecommendationResponse).
 */
public record TeamMemberRecommendationResponse(
        Instrument instrument,
        List<Candidate> candidates
) {

    public record Candidate(UserSummaryResponse user, double score, List<String> reasons) {
    }
}
