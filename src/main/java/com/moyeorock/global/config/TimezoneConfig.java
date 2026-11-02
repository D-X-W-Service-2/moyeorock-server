package com.moyeorock.global.config;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;
import org.springframework.context.annotation.Configuration;

// LocalDateTime.now()(JPA auditing, joinedAt, @Future 검증 등)는 JVM 기본 타임존을 따른다.
// 서버 OS가 UTC여도 한국 시간 기준으로 동작하도록 기본 타임존을 고정한다.
// datasource url의 serverTimezone은 JDBC 드라이버 설정이라 JVM 기본 타임존과는 별개다.
// main()이 아니라 여기서 거는 이유: @SpringBootTest는 main을 거치지 않아 테스트 컨텍스트에도 반영하려고.
@Configuration
public class TimezoneConfig {

    private static final String DEFAULT_TIMEZONE = "Asia/Seoul";

    @PostConstruct
    void setDefaultTimezone() {
        TimeZone.setDefault(TimeZone.getTimeZone(DEFAULT_TIMEZONE));
    }
}
