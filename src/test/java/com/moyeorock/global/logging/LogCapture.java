package com.moyeorock.global.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

// 로그 문자열이 아니라 이벤트 시점의 MDC 맵을 그대로 검증하기 위한 테스트 헬퍼.
// (테스트 application.yml은 logging.pattern을 안 쓰므로 출력 문자열엔 requestId가 안 보인다.)
final class LogCapture implements AutoCloseable {

    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    LogCapture() {
        appender.start();
        logger.addAppender(appender);
    }

    List<ILoggingEvent> events() {
        return appender.list;
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }
}
