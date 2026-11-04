package com.moyeorock.domain.setlist.dto.response;

import java.util.List;

// 팀 선택곡 저장·조회 공통 응답
public record TeamSongsResponse(Long teamId, List<TeamSongResponse> songs) {
}
