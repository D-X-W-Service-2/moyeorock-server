package com.moyeorock.global.common.enums;

// 값 근거: docs/conventions/erd.md `recruit_posts.target_type` · `join_requests.target_type`
// BookmarkTargetType(TEAM|USER|SONG)·NotificationTargetType과 다르다 — 공유 금지 (domains.md 공용 enum 절)
public enum TargetType {
    TEAM, GROUP
}
