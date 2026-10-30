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
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * auth → user 단방향. UserRepository 직접 사용은 architecture.md §3이 허용한 예외 (auth-계획 Au-1).
 * User 엔티티는 밖으로 나가지 않는다 — 응답은 AuthTokenResponse·KakaoAuthResponse.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final KakaoOAuthClient kakaoOAuthClient;
    private final KakaoSignupTokenProvider signupTokenProvider;
    private final NicknameGenerator nicknameGenerator;

    /** users.nickname UNIQUE 인덱스 이름(V2__create_remaining_domain_tables.sql). MySQL 중복 오류 메시지 "for key 'users.uk_users_nickname'"로 어느 제약에 걸렸는지 가른다. */
    static final String NICKNAME_UNIQUE_INDEX = "uk_users_nickname";
    /** 임시 닉네임 충돌 시 재시도 포함 저장 시도 횟수. */
    static final int MAX_SAVE_ATTEMPTS = 2;

    /**
     * 이메일 가입. 가입 직후 로그인 상태로 넘긴다(dto-naming §1). privacyAgreed는 @AssertTrue로 이미 검증됨.
     * @Transactional을 붙이지 않는다: 쓰기가 saveAndFlush 1번뿐이라 묶을 스냅샷이 없고(conventions.md §3 기준),
     * 임시 닉네임 충돌 재시도(saveNewUser)는 실패한 INSERT의 트랜잭션이 끝나 있어야 가능하다.
     */
    public AuthTokenResponse signup(UserSignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }
        String passwordHash = passwordEncoder.encode(request.password());
        LocalDateTime agreedAt = LocalDateTime.now();
        User user = saveNewUser(request.name(),
                nickname -> User.signupWithEmail(request.email(), passwordHash, nickname, agreedAt),
                ErrorCode.EMAIL_DUPLICATED);
        return issueToken(user);
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

    /** 카카오 가입 완료. 약관 동의 후 signupToken으로 계정을 만든다. email은 저장하지 않는다(Au-6). 트랜잭션은 signup과 같은 이유로 없다. */
    public AuthTokenResponse kakaoSignup(KakaoSignupRequest request) {
        SignupClaims claims = signupTokenProvider.parse(request.signupToken());
        if (userRepository.findByKakaoId(claims.kakaoId()).isPresent()) {
            throw new BusinessException(ErrorCode.ALREADY_REGISTERED);
        }
        LocalDateTime agreedAt = LocalDateTime.now();
        User user = saveNewUser(claims.kakaoNickname(),
                nickname -> User.signupWithKakao(claims.kakaoId(), nickname, agreedAt),
                ErrorCode.ALREADY_REGISTERED);
        return issueToken(user);
    }

    /**
     * 사전 검증을 통과했더라도 동시 가입이 정확히 겹치면 users UNIQUE 3개(email·kakao_id·nickname) 중 하나에 걸린다.
     * 어느 제약인지로 처리가 갈린다(PR #72 리뷰).
     * - email·kakao_id: 같은 계정의 동시 가입 → 500 대신 409(onDuplicate)
     * - nickname: 서버가 만든 임시 닉네임이 다른 가입과 겹친 것이라 사용자가 고칠 수 없다 → 닉네임을 다시 만들어 1회 재시도.
     *   NicknameGenerator는 이미 점유된 base에 suffix를 붙이므로 재호출만으로 새 후보가 나온다.
     *   재시도까지 겹치는 경우는 사실상 없으므로 그대로 흘려 500으로 둔다(NicknameGenerator의 IllegalStateException과 같은 취급).
     */
    private User saveNewUser(String rawName, Function<String, User> newUser, ErrorCode onDuplicate) {
        DataIntegrityViolationException nicknameCollision = null;
        for (int attempt = 1; attempt <= MAX_SAVE_ATTEMPTS; attempt++) {
            String nickname = nicknameGenerator.resolve(rawName);
            try {
                return userRepository.saveAndFlush(newUser.apply(nickname));
            } catch (DataIntegrityViolationException e) {
                if (!violatesNicknameUnique(e)) {
                    throw new BusinessException(onDuplicate);
                }
                nicknameCollision = e;
            }
        }
        throw nicknameCollision;
    }

    private static boolean violatesNicknameUnique(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(NICKNAME_UNIQUE_INDEX);
    }

    private AuthTokenResponse issueToken(User user) {
        return AuthTokenResponse.of(user, jwtProvider.generateToken(user.getId()));
    }
}
