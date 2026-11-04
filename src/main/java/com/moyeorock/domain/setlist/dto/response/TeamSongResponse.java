package com.moyeorock.domain.setlist.dto.response;

import com.moyeorock.domain.setlist.entity.TeamSong;
import com.moyeorock.domain.song.dto.response.SongSummaryResponse;

// 팀 선택곡 한 줄. 팀 상세의 songs(2팀)와 선택곡 저장·조회 응답이 같이 쓴다(dto-naming §10).
public record TeamSongResponse(Long id, SongSummaryResponse song) {

    // 곡 정보는 song 도메인 Service에서 받아 오므로 엔티티 하나로는 만들 수 없다 → of
    public static TeamSongResponse of(TeamSong teamSong, SongSummaryResponse song) {
        return new TeamSongResponse(teamSong.getId(), song);
    }
}
