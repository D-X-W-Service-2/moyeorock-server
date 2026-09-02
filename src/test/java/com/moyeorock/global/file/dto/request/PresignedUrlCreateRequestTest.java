package com.moyeorock.global.file.dto.request;

import com.moyeorock.global.file.FileDomain;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// 컨트롤러 테스트는 Jackson SNAKE_CASE 설정 탓에 contentType이 바인딩되지 않아
// @NotBlank로도 400이 나온다 — 화이트리스트(@Pattern) 규칙 자체는 여기서 바인딩 없이 검증한다
class PresignedUrlCreateRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<ConstraintViolation<PresignedUrlCreateRequest>> validate(String contentType) {
        return validator.validate(
                new PresignedUrlCreateRequest(FileDomain.PROFILE_IMAGE, "photo.png", contentType));
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/jpeg", "image/png", "image/gif"})
    @DisplayName("허용 이미지 타입은 통과한다")
    void allowedContentTypes(String contentType) {
        assertThat(validate(contentType)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/html", "image/svg+xml", "application/pdf", "image/jpg", "IMAGE/PNG", "image/png "})
    @DisplayName("허용 목록에 없는 contentType은 거절한다")
    void disallowedContentTypes(String contentType) {
        assertThat(validate(contentType))
                .anyMatch(v -> v.getPropertyPath().toString().equals("contentType"));
    }
}
