package com.moyeorock.domain.song.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

// 환경변수 AI_SONG_SYNC_ADMIN_USER_IDS는 쉼표로 구분한 문자열이다. 비어 있으면(기본값) 빈 목록이어야 아무도 못 부른다.
class SongSyncPropertiesTest {

    @Test
    @DisplayName("쉼표로 구분한 사용자 id를 목록으로 읽는다")
    void bindsCommaSeparatedIds() {
        assertThat(bind("3, 15,42").adminUserIds()).containsExactly(3L, 15L, 42L);
    }

    @Test
    @DisplayName("값이 비어 있으면 빈 목록이다")
    void bindsEmptyValueAsEmptyList() {
        assertThat(bind("").adminUserIds()).isEmpty();
    }

    private static SongSyncProperties bind(String adminUserIds) {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "ai.song-sync.admin-user-ids", adminUserIds)));
        return binder.bindOrCreate("ai.song-sync", SongSyncProperties.class);
    }
}
