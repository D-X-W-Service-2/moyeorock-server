package com.moyeorock.global.file;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 업로드 파일의 용도. directory가 스토리지 키의 최상위 경로가 된다 (예: performance/uuid.png).
 * PERFORMANCE_POSTER만 명세(dto-spec.md §10 예시)에 있고,
 * PROFILE_IMAGE·GROUP_COVER는 ERD의 이미지 컬럼(users.profile_image, groups.cover_image) 기준 제안값 — 팀 확정 필요.
 */
@Getter
@RequiredArgsConstructor
public enum FileDomain {

    PROFILE_IMAGE("profile"),
    GROUP_COVER("group"),
    PERFORMANCE_POSTER("performance");

    private final String directory;
}
