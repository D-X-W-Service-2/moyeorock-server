package com.moyeorock.domain.user.enums;

// 컬럼 없음. `users.kakao_id` 유무로 파생한다 (User.loginType()).
// 근거: api-spec §11 "이메일 회원 ↔ 카카오 회원 중복 X" → kakao_id != null ⇔ KAKAO 가 항상 성립.
public enum LoginType {
    EMAIL, KAKAO
}
