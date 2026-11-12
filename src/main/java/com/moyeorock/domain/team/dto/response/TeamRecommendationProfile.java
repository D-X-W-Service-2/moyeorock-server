package com.moyeorock.domain.team.dto.response;

import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import java.util.List;

/** 곡 추천에 필요한 팀 정보만 다른 도메인에 전달한다. */
public record TeamRecommendationProfile(Long performanceId, List<Genre> genres, List<MemberPart> members) {
    public record MemberPart(Long userId, Instrument instrument) {
    }
}
