package com.moyeorock.global.file.enums;

// erd.md #17 참고. users.profile_image·groups_.cover_image·performances.poster_image
// 3곳 외에 이미지 컬럼이 없으므로 이 3개뿐이다. 폴더명은 이 값의 소문자 형태를 쓰지 않고
// FileService가 직접 매핑한다(값 이름과 폴더명이 1:1로 안 맞을 수 있어서).
public enum UploadDomain {
    PROFILE_IMAGE,
    GROUP_COVER,
    PERFORMANCE_POSTER
}
