package com.moyeorock.domain.song.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI 곡 동기화 스케줄({@code SongService#syncFromAiOnSchedule})을 {@code ai.song-sync.enabled=true}일 때만 켠다.
 * 로컬·테스트 기본값은 false라 스케줄이 돌지 않는다. 지금 {@code @Scheduled}는 곡 동기화 하나뿐이다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "ai.song-sync.enabled", havingValue = "true")
public class SongSyncConfig {
}
