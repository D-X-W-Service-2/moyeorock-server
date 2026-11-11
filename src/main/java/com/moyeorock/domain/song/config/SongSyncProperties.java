package com.moyeorock.domain.song.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * AI 곡 동기화 설정 중 수동 동기화 권한(이슈 #162). 스케줄 켜기({@code enabled})·주기({@code cron})는
 * SongSyncConfig·{@code @Scheduled}가 직접 읽는다.
 *
 * @param adminUserIds 수동 동기화({@code POST /v1/songs/sync})를 부를 수 있는 사용자 id. 서비스에 관리자 권한이 없어
 *                     환경변수({@code AI_SONG_SYNC_ADMIN_USER_IDS}, 쉼표로 구분)로 정한다. 비어 있으면 아무도 부를 수 없다
 */
@ConfigurationProperties(prefix = "ai.song-sync")
public record SongSyncProperties(
        @DefaultValue List<Long> adminUserIds
) {
}
