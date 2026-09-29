package com.moyeorock.domain.auth.service;

import com.moyeorock.domain.auth.client.KakaoOAuthClient;
import com.moyeorock.domain.auth.client.KakaoUserInfo;
import com.moyeorock.domain.auth.dto.request.KakaoSignupRequest;
import com.moyeorock.domain.auth.dto.request.UserLoginRequest;
import com.moyeorock.domain.auth.dto.request.UserSignupRequest;
import com.moyeorock.domain.auth.dto.response.AuthTokenResponse;
import com.moyeorock.domain.auth.dto.response.KakaoAuthResponse;
import com.moyeorock.domain.auth.service.KakaoSignupTokenProvider.SignupClaims;
import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.enums.UserStatus;
import com.moyeorock.domain.user.repository.UserRepository;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import com.moyeorock.global.security.JwtProvider;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * auth → user 단방향. UserRepository 직접 사용은 architecture.md §3이 허용한 예외 (auth-계획 Au-1).
 * User 엔티티는 밖으로 나가지 않는다 — 응답은 AuthTokenResponse·KakaoAuthResponse.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final KakaoOAuthClient kakaoOAuthClient;
    private final KakaoSignupTokenProvider signupTokenProvider;
    private final NicknameGenerator nicknameGenerator;

    /** 이메일 가입. 가입 직후 로그인 상태로 넘긴다(dto-naming §1). privacyAgreed는 @AssertTrue로 이미 검증됨. */
    @Transactional
    public AuthTokenResponse signup(UserSignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }
        String nickname = nicknameGenerator.resolve(request.name());
        User user = User.signupWithEmail(request.email(), passwordEncoder.encode(request.password()),
                nickname, LocalDateTime.now());
        return issueToken(saveNewUser(user, ErrorCode.EMAIL_DUPLICATED));
    }

    /**
     * 이메일 로그인. 이메일 없음·비밀번호 불일치·카카오 전용 계정·탈퇴/정지 계정을 전부 같은 401로 낸다 —
     * 어느 쪽이 틀렸는지 알려주면 계정 존재 여부가 노출된다.
     */
    public AuthTokenResponse login(UserLoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .filter(found -> found.getPasswordHash() != null)
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .filter(found -> found.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));
        return issueToken(user);
    }

    /**
     * 카카오 인증. 계정을 만들지 않는다. 기존 회원이면 로그인 토큰, 신규면 가입용 토큰을 돌려준다 —
     * 인가 코드가 1회용이라 신규 판정 후 다시 호출할 수 없기 때문 (2026-09-28 결정).
     */
    public KakaoAuthResponse kakaoAuth(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        KakaoUserInfo info = kakaoOAuthClient.fetchUser(code);
        return userRepository.findByKakaoId(info.kakaoId())
                .map(user -> {
                    if (user.getStatus() != UserStatus.ACTIVE) {
                        throw new BusinessException(ErrorCode.LOGIN_FAILED);
                    }
                    return KakaoAuthResponse.loggedIn(issueToken(user));
                })
                .orElseGet(() -> KakaoAuthResponse.signupRequired(
                        signupTokenProvider.issue(info.kakaoId(), info.nickname()), info.nickname()));
    }

    /** 카카오 가입 완료. 약관 동의 후 signupToken으로 계정을 만든다. email은 저장하지 않는다(Au-6). */
    @Transactional
    public AuthTokenResponse kakaoSignup(KakaoSignupRequest request) {
        SignupClaims claims = signupTokenProvider.parse(request.signupToken());
        if (userRepository.findByKakaoId(claims.kakaoId()).isPresent()) {
            throw new BusinessException(ErrorCode.ALREADY_REGISTERED);
        }
        String nickname = nicknameGenerator.resolve(claims.kakaoNickname());
        User user = User.signupWithKakao(claims.kakaoId(), nickname, LocalDateTime.now());
        return issueToken(saveNewUser(user, ErrorCode.ALREADY_REGISTERED));
    }

    /** 사전 검증을 통과했더라도 동시 가입이 정확히 겹치면 UNIQUE(email·kakao_id)에 걸린다 → 500 대신 409. */
    private User saveNewUser(User user, ErrorCode onDuplicate) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(onDuplicate);
        }
    }

    private AuthTokenResponse issueToken(User user) {
        return AuthTokenResponse.of(user, jwtProvider.generateToken(user.getId()));
    }
}
