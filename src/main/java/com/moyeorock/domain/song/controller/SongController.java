package com.moyeorock.domain.song.controller;

import com.moyeorock.domain.song.dto.response.SongResponse;
import com.moyeorock.domain.song.service.SongService;
import com.moyeorock.global.common.dto.ApiResponse;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.common.enums.Genre;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/songs")
@RequiredArgsConstructor
public class SongController {

    private final SongService songService;

    /** keyword는 제목·아티스트 부분 일치, genre는 선택. 정렬은 제목 순 고정이라 {@code sort}를 보내도 쓰지 않는다. */
    @Operation(summary = "곡 검색")
    @GetMapping
    public ApiResponse<PageResponse<SongResponse>> search(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) Genre genre, @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(songService.search(keyword, genre, pageable));
    }
}
