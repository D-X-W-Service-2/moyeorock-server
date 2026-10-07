package com.moyeorock.domain.notice.controller;

import com.moyeorock.domain.notice.dto.request.NoticeCreateRequest;
import com.moyeorock.domain.notice.dto.request.NoticeUpdateRequest;
import com.moyeorock.domain.notice.dto.response.NoticeDetailResponse;
import com.moyeorock.domain.notice.dto.response.NoticeSummaryResponse;
import com.moyeorock.domain.notice.service.NoticeService;
import com.moyeorock.global.common.dto.ApiResponse;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공지는 생성·목록만 모임 하위 경로({@code /v1/groups/{groupId}/notices})를 쓰고,
 * 단건 조회·수정·삭제는 단독 경로({@code /v1/notices/{noticeId}})를 쓴다(api-conventions.md §5).
 * 경로가 둘이라 클래스 레벨 {@code @RequestMapping}을 두지 않는다.
 */
@RestController
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @PostMapping("/v1/groups/{groupId}/notices")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<NoticeDetailResponse> create(@AuthUser Long userId, @PathVariable Long groupId,
            @Valid @RequestBody NoticeCreateRequest request) {
        return ApiResponse.success(noticeService.create(userId, groupId, request));
    }

    @GetMapping("/v1/groups/{groupId}/notices")
    public ApiResponse<PageResponse<NoticeSummaryResponse>> getNotices(@AuthUser Long userId,
            @PathVariable Long groupId, @RequestParam(required = false) Boolean isPinned,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(noticeService.getNotices(userId, groupId, isPinned, pageable));
    }

    @GetMapping("/v1/notices/{noticeId}")
    public ApiResponse<NoticeDetailResponse> getNotice(@AuthUser Long userId, @PathVariable Long noticeId) {
        return ApiResponse.success(noticeService.getNotice(userId, noticeId));
    }

    @PutMapping("/v1/notices/{noticeId}")
    public ApiResponse<NoticeDetailResponse> update(@AuthUser Long userId, @PathVariable Long noticeId,
            @Valid @RequestBody NoticeUpdateRequest request) {
        return ApiResponse.success(noticeService.update(userId, noticeId, request));
    }

    @DeleteMapping("/v1/notices/{noticeId}")
    public ApiResponse<Void> delete(@AuthUser Long userId, @PathVariable Long noticeId) {
        noticeService.delete(userId, noticeId);
        return ApiResponse.success(null);
    }
}
