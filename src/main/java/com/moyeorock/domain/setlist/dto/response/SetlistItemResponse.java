package com.moyeorock.domain.setlist.dto.response;

import com.moyeorock.domain.setlist.entity.Setlist;
import com.moyeorock.domain.song.dto.response.SongSummaryResponse;

/**
 * 공연 셋리스트 한 줄(dto-naming §10). 팀 이름은 해체된 팀도 나온다 — 끝난 공연의 셋리스트를 남기기 때문이다.
 * 확정 전에는 {@code sortOrder}가 null이다.
 */
public record SetlistItemResponse(Long teamId, String teamName, SongSummaryResponse song, Integer sortOrder) {

    // 팀 이름·곡 정보는 각 도메인 Service에서 받아 오므로 엔티티 하나로는 만들 수 없다 → of
    public static SetlistItemResponse of(Setlist setlist, String teamName, SongSummaryResponse song) {
        return new SetlistItemResponse(setlist.getTeamId(), teamName, song, setlist.getSortOrder());
    }
}
