package com.moyeorock.domain.practice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 연습 녹음 AI 분석 전용 실행기. 스레드가 1개라 서버 전체에서 분석은 한 번에 하나만 돈다(계획 D-4).
 *
 * <p>{@code @Async}·{@code @EnableAsync}는 쓰지 않는다(architecture.md §4) — 요청 트랜잭션이 커밋된 뒤
 * PracticeRecordingService가 이 실행기에 직접 넘긴다. 이 빈이 생기면 Spring Boot가 기본 실행기(applicationTaskExecutor)를
 * 만들지 않으므로 application.yml에 {@code spring.task.execution.mode: force}를 둬서 기본 실행기도 그대로 둔다 —
 * 나중에 누가 기본 실행기를 쓰더라도 이 단일 스레드 실행기가 대신 쓰이지 않게 하려는 것이다.
 */
@Configuration
@EnableConfigurationProperties(PracticeProperties.class)
public class PracticeAnalysisConfig {

    public static final String EXECUTOR_BEAN_NAME = "practiceAnalysisExecutor";

    // 한 사람당 진행 중 분석이 하나라 대기열 길이는 "분석을 기다리는 사람 수"다. 넘치면 그 녹음을 FAILED로 바꾼다.
    private static final int QUEUE_CAPACITY = 100;

    @Bean(EXECUTOR_BEAN_NAME)
    public ThreadPoolTaskExecutor practiceAnalysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("practice-analysis-");
        // 재시작(CD 배포) 때 분석이 끝나길 기다리지 않는다 — 남은 건은 다음 부팅 때 FAILED로 정리한다
        // (PracticeAnalysisService.failUnfinishedOnStartup).
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
