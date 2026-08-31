package com.moyeorock.global.common.dto;

import com.moyeorock.global.exception.ErrorCode;
import java.util.List;

public record ErrorResponse(String code, String message, List<FieldErrorResponse> fieldErrors) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldErrorResponse> fieldErrors) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), fieldErrors);
    }
}
