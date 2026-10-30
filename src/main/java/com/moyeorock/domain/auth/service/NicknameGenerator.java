package com.moyeorock.domain.auth.service;

import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.repository.UserRepository;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 가입 시 임시 닉네임 결정. 가입 요청의 name(카카오는 프로필 닉네임)을 그대로 쓰되,
 * users.nickname이 UNIQUE라 이미 있으면 "_"+영숫자 4자를 붙여 재시도한다 (2026-09-28 결정, 검토 문서 LB-2).
 * 정식 닉네임은 온보딩에서 다시 받으므로 임시값의 모양은 자유다. 최대 20자(15 + "_" + 4).
 */
@Component
@RequiredArgsConstructor
public class NicknameGenerator {

    static final int BASE_MAX_LENGTH = 15;
    static final int SUFFIX_LENGTH = 4;
    static final int MAX_ATTEMPTS = 5;
    static final String FALLBACK_BASE = "회원";
    private static final String ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyz0123456789";

    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();

    public String resolve(String rawName) {
        String base = normalize(rawName);
        // "탈퇴회원" 접두사는 탈퇴 치환용 예약어라 처음부터 suffix를 붙인다
        boolean reserved = base.startsWith(User.WITHDRAWN_NICKNAME_PREFIX);
        if (!reserved && !userRepository.existsByNickname(base)) {
            return base;
        }
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = base + "_" + randomSuffix();
            if (!userRepository.existsByNickname(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("임시 닉네임 생성 실패: " + base);
    }

    private String normalize(String rawName) {
        String trimmed = rawName == null ? "" : rawName.strip();
        if (trimmed.isEmpty()) {
            trimmed = FALLBACK_BASE;
        }
        return trimmed.length() > BASE_MAX_LENGTH ? trimmed.substring(0, BASE_MAX_LENGTH) : trimmed;
    }

    private String randomSuffix() {
        StringBuilder sb = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            sb.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }
}
