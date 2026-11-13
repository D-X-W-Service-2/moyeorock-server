package com.moyeorock.global.file.enums;

// erd.md #17 참고. 이미지 3개는 users.profile_image·groups_.cover_image·performances.poster_image에
// URL 문자열로 저장된다. PRACTICE_AUDIO(개인 연습 녹음, 2026-11-09 추가)는 본인만 듣는 비공개 파일이라
// 연습 도메인이 URL이 아니라 스토리지 key를 저장하고 읽을 때마다 임시 URL을 만든다(FileService.issueReadUrl).
// 도메인별 폴더·허용 형식·크기 한도는 FileService가 정한다(값 이름과 폴더명이 1:1로 안 맞을 수 있어서).
public enum UploadDomain {
    PROFILE_IMAGE,
    GROUP_COVER,
    PERFORMANCE_POSTER,
    PRACTICE_AUDIO
}
