package com.moyeorock.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimezoneConfigTest {

    private final TimeZone originalTimezone = TimeZone.getDefault();

    // 기본 타임존은 JVM 전역 상태라 다른 테스트에 새지 않도록 되돌린다
    @AfterEach
    void restoreDefaultTimezone() {
        TimeZone.setDefault(originalTimezone);
    }

    @Test
    @DisplayName("서버 기본 타임존이 UTC여도 Asia/Seoul로 바꾼다")
    void setDefaultTimezone_overridesUtc() {
        // 개발 PC가 이미 KST면 설정 없이도 통과하므로 UTC 서버 상황을 먼저 만든다
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        new TimezoneConfig().setDefaultTimezone();

        assertThat(TimeZone.getDefault().getID()).isEqualTo("Asia/Seoul");
    }
}
