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

    // 모임 (group) — 코드·메시지는 Notion API 초안 에러 응답 예시와 맞춘다
    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 모임입니다."),
    GROUP_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 모임의 모임원이 아닙니다."),
    NOT_GROUP_MEMBER(HttpStatus.FORBIDDEN, "모임원만 조회할 수 있습니다."),
    NOT_GROUP_OWNER(HttpStatus.FORBIDDEN, "모임장만 할 수 있습니다."),
    CANNOT_CHANGE_OWN_ROLE(HttpStatus.BAD_REQUEST, "자기 자신의 역할은 변경할 수 없습니다."),
    OWNER_CANNOT_LEAVE(HttpStatus.CONFLICT, "모임장은 위임 후 탈퇴할 수 있습니다."),

    // 공지 (notice)
    NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 공지입니다.");

    private final HttpStatus status;
    private final String message;
}
