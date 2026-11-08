package com.moyeorock.domain.user.dto.response;

import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Level;
import com.moyeorock.global.common.enums.Region;
import java.util.List;

/**
 * 추천 후보 사용자 한 명({@code UserService.findRecommendationCandidates}). 2팀 팀원 추천이 AI 서버에 후보로 보낸다.
 * {@code level}은 후보를 찾을 때 지정한 악기에서의 실력이다. 온보딩 전 사용자는 region·genres가 null일 수 있다.
 */
public record RecommendationCandidateResponse(
        Long userId,
        Region region,
        List<Genre> genres,
        Level level
) {
}
