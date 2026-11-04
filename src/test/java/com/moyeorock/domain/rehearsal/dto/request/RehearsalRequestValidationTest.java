package com.moyeorock.domain.rehearsal.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RehearsalRequestValidationTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 14, 19, 0);
    private static final LocalDateTime END = START.plusHours(2);

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("합주 생성: 제목·시작·종료만 있으면 통과하고 장소·메모는 비워도 된다")
    void create_passes_withOnlyRequiredFields() {
        var request = new RehearsalCreateRequest("1차 합주", START, END, null, null);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("합주 생성: 제목이 비었거나 101자, 장소가 101자면 각각 거절된다")
    void create_rejects_blankTitle_longTitle_longPlace() {
        var blank = new RehearsalCreateRequest(" ", START, END, null, null);
        var longTitle = new RehearsalCreateRequest("가".repeat(101), START, END, null, null);
        var longPlace = new RehearsalCreateRequest("합주", START, END, "가".repeat(101), null);

        assertThat(validator.validate(blank)).hasSize(1);
        assertThat(validator.validate(longTitle)).hasSize(1);
        assertThat(validator.validate(longPlace)).hasSize(1);
    }

    @Test
    @DisplayName("합주 생성: 시작·종료 시각이 없으면 각각 거절된다")
    void create_rejects_missingTimes() {
        var request = new RehearsalCreateRequest("합주", null, null, null, null);

        assertThat(validator.validate(request)).hasSize(2);
    }

    @Test
    @DisplayName("합주 수정: 생성과 같은 규칙으로 통과·거절된다")
    void update_followsSameRules() {
        var valid = new RehearsalUpdateRequest("합주", START, END, null, null);
        var blank = new RehearsalUpdateRequest(" ", START, END, null, null);
        var longPlace = new RehearsalUpdateRequest("합주", START, END, "가".repeat(101), null);
        var missingTimes = new RehearsalUpdateRequest("합주", null, null, null, null);

        assertThat(validator.validate(valid)).isEmpty();
        assertThat(validator.validate(blank)).hasSize(1);
        assertThat(validator.validate(longPlace)).hasSize(1);
        assertThat(validator.validate(missingTimes)).hasSize(2);
    }

    @Test
    @DisplayName("합주 생성·수정: 메모는 20,000자까지 통과하고 20,001자부터 거절된다")
    void memo_allowsUpTo20000Chars() {
        var createOk = new RehearsalCreateRequest("합주", START, END, null, "가".repeat(20000));
        var createTooLong = new RehearsalCreateRequest("합주", START, END, null, "가".repeat(20001));
        var updateOk = new RehearsalUpdateRequest("합주", START, END, null, "가".repeat(20000));
        var updateTooLong = new RehearsalUpdateRequest("합주", START, END, null, "가".repeat(20001));

        assertThat(validator.validate(createOk)).isEmpty();
        assertThat(validator.validate(createTooLong)).hasSize(1);
        assertThat(validator.validate(updateOk)).isEmpty();
        assertThat(validator.validate(updateTooLong)).hasSize(1);
    }
}
