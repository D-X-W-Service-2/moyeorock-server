package com.moyeorock.domain.auth.client;

/** 카카오 사용자 조회 결과 중 우리가 쓰는 두 값. 엔티티가 아니라 client → service 내부 전달용. */
public record KakaoUserInfo(String kakaoId, String nickname) {
}
