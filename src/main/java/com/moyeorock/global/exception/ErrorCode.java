package com.moyeorock.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    NO_PERMISSION(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type입니다."),
    INVALID_STATE(HttpStatus.CONFLICT, "처리할 수 없는 상태입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // 모집 공고 (recruit) — 코드 문자열은 명세에 없어 team 도메인 패턴(TEAM_NOT_FOUND 등)을 따랐다
    RECRUIT_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 모집 공고입니다."),
    NOT_POST_AUTHOR(HttpStatus.FORBIDDEN, "작성자만 할 수 있습니다.");

    private final HttpStatus status;
    private final String message;
}
