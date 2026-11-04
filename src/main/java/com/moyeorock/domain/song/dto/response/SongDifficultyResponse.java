package com.moyeorock.domain.song.dto.response;

import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;

// 파트별 난이도 한 칸. songs.difficulty JSON(객체)을 응답에서만 [{part, level}] 배열로 펼친다(dto-spec §9, 이슈 #103).
public record SongDifficultyResponse(Instrument part, Level level) {
}
