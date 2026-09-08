package com.moyeorock.global.exception;

import com.moyeorock.global.common.dto.ApiResponse;
import com.moyeorock.global.common.dto.ErrorResponse;
import com.moyeorock.global.common.dto.FieldErrorResponse;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return toResponse(errorCode, ErrorResponse.of(errorCode));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        List<FieldErrorResponse> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new FieldErrorResponse(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
        return toResponse(ErrorCode.VALIDATION_FAILED, ErrorResponse.of(ErrorCode.VALIDATION_FAILED, fieldErrors));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException e) {
        return toResponse(ErrorCode.NO_PERMISSION, ErrorResponse.of(ErrorCode.NO_PERMISSION));
    }

    // 아래 6개는 Spring MVC가 기본으로 4xx로 내리던 예외 — catch-all이 가로채면 500이 되므로 원래 상태 코드로 명시한다

    // 없는 경로 — 기본 설정에선 정적 리소스 핸들러가 NoResourceFoundException을,
    // spring.web.resources.add-mappings=false로 끄면 NoHandlerFoundException을 던지므로 둘 다 잡는다
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(Exception e) {
        return toResponse(ErrorCode.RESOURCE_NOT_FOUND, ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND));
    }

    // 요청 바디 파싱 실패 (깨진 JSON, 존재하지 않는 enum 값)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("요청 바디 파싱 실패: {}", e.getMessage());
        return toResponse(ErrorCode.VALIDATION_FAILED, ErrorResponse.of(ErrorCode.VALIDATION_FAILED));
    }

    // 쿼리 파라미터·경로 변수 타입 변환 실패 (예: Long 자리에 문자열, 없는 enum 값)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        List<FieldErrorResponse> fieldErrors = List.of(
                new FieldErrorResponse(e.getName(), "올바른 값이 아닙니다."));
        return toResponse(ErrorCode.VALIDATION_FAILED, ErrorResponse.of(ErrorCode.VALIDATION_FAILED, fieldErrors));
    }

    // 필수 쿼리 파라미터 누락
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException e) {
        List<FieldErrorResponse> fieldErrors = List.of(
                new FieldErrorResponse(e.getParameterName(), "필수 파라미터입니다."));
        return toResponse(ErrorCode.VALIDATION_FAILED, ErrorResponse.of(ErrorCode.VALIDATION_FAILED, fieldErrors));
    }

    // 경로는 있지만 해당 HTTP 메서드가 매핑되지 않은 경우 — 405는 HTTP 규격상 지원 메서드를 Allow 헤더로 알려야 한다
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        Set<HttpMethod> supported = e.getSupportedHttpMethods();
        HttpMethod[] allow = supported == null ? new HttpMethod[0] : supported.toArray(HttpMethod[]::new);
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getStatus())
                .allow(allow)
                .body(ApiResponse.error(ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED)));
    }

    // 요청 Content-Type을 받을 수 없는 경우 (예: JSON 엔드포인트에 text/plain)
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return toResponse(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorResponse.of(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return toResponse(ErrorCode.INTERNAL_SERVER_ERROR, ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode, ErrorResponse errorResponse) {
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.error(errorResponse));
    }
}
