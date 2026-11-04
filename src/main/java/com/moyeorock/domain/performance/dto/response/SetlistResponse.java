package com.moyeorock.domain.performance.dto.response;

import com.moyeorock.domain.performance.entity.Performance;
import com.moyeorock.domain.setlist.dto.response.SetlistItemResponse;
import java.util.List;

/**
 * 공연 셋리스트 조회·넘기기·확정·확정 해제 공통 응답(dto-naming §9). 확정 전에는 넘겨진 곡 전체(sortOrder null),
 * 확정 후에는 확정곡만 순서대로 담는다.
 */
public record SetlistResponse(Long performanceId, boolean confirmed, List<SetlistItemResponse> setlist) {

    // 셋리스트 행은 setlist 도메인 Service에서 받아 오므로 엔티티 하나로는 만들 수 없다 → of
    public static SetlistResponse of(Performance performance, List<SetlistItemResponse> setlist) {
        return new SetlistResponse(performance.getId(), performance.isSetlistConfirmed(), setlist);
    }
}
