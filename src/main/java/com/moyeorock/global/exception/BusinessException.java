package com.moyeorock.global.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    /** 기본 메시지 대신 상황 값을 담은 문장을 내려야 할 때(예: 탈퇴를 막은 팀 이름). code·status는 그대로다. */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
