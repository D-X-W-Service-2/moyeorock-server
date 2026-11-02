package com.moyeorock.domain.bookmark.controller;

import com.moyeorock.domain.bookmark.dto.request.BookmarkCreateRequest;
import com.moyeorock.domain.bookmark.dto.response.BookmarkResponse;
import com.moyeorock.domain.bookmark.enums.BookmarkTargetType;
import com.moyeorock.domain.bookmark.service.BookmarkService;
import com.moyeorock.global.common.dto.ApiResponse;
import com.moyeorock.global.common.dto.DeleteResponse;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BookmarkResponse> create(@AuthUser Long userId,
            @Valid @RequestBody BookmarkCreateRequest request) {
        return ApiResponse.success(bookmarkService.create(userId, request));
    }

    @GetMapping
    public ApiResponse<PageResponse<BookmarkResponse>> getBookmarks(@AuthUser Long userId,
            @RequestParam(required = false) BookmarkTargetType targetType,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(bookmarkService.getBookmarks(userId, targetType, pageable));
    }

    @DeleteMapping("/{bookmarkId}")
    public ApiResponse<DeleteResponse> delete(@AuthUser Long userId, @PathVariable Long bookmarkId) {
        return ApiResponse.success(bookmarkService.delete(userId, bookmarkId));
    }
}
