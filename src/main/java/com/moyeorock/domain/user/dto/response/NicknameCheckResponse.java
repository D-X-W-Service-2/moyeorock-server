package com.moyeorock.domain.user.dto.response;

/** 저장하지 않고 사용 가능 여부만 돌려준다. */
public record NicknameCheckResponse(String nickname, boolean available) {
}
