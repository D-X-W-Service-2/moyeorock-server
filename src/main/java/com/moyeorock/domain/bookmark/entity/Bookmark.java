package com.moyeorock.domain.bookmark.entity;

import com.moyeorock.domain.bookmark.enums.BookmarkTargetType;
import com.moyeorock.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// target_team_id·target_user_id·target_song_id는 exclusive-arc(erd.md §3) — 셋 중 정확히
// 하나만 값을 가져야 한다. DB CHECK는 안 건다(CLAUDE.md 절대 규칙 6) — create()의 Java 검증이
// 유일한 강제 수단이다(recruit_posts와 동일 패턴).
@Entity
@Table(name = "bookmarks",
        indexes = {
                @Index(name = "uk_bookmarks_user_id_target_team_id", columnList = "user_id, target_team_id", unique = true),
                @Index(name = "uk_bookmarks_user_id_target_user_id", columnList = "user_id, target_user_id", unique = true),
                @Index(name = "uk_bookmarks_user_id_target_song_id", columnList = "user_id, target_song_id", unique = true)
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bookmark extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "target_team_id")
    private Long targetTeamId;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(name = "target_song_id")
    private Long targetSongId;

    public static Bookmark create(Long userId, Long targetTeamId, Long targetUserId, Long targetSongId) {
        long nonNullCount = java.util.stream.Stream.of(targetTeamId, targetUserId, targetSongId)
                .filter(java.util.Objects::nonNull).count();
        if (nonNullCount != 1) {
            throw new IllegalArgumentException(
                    "targetTeamId, targetUserId, targetSongId 중 정확히 하나만 있어야 합니다.");
        }
        Bookmark bookmark = new Bookmark();
        bookmark.userId = userId;
        bookmark.targetTeamId = targetTeamId;
        bookmark.targetUserId = targetUserId;
        bookmark.targetSongId = targetSongId;
        return bookmark;
    }

    public BookmarkTargetType getTargetType() {
        if (targetTeamId != null) {
            return BookmarkTargetType.TEAM;
        }
        return targetUserId != null ? BookmarkTargetType.USER : BookmarkTargetType.SONG;
    }

    public Long getTargetId() {
        if (targetTeamId != null) {
            return targetTeamId;
        }
        return targetUserId != null ? targetUserId : targetSongId;
    }
}
