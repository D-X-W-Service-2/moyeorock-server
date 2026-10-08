package com.moyeorock.domain.auth.service;

import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import com.moyeorock.global.security.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 카카오 2단계 가입용 토큰. 카카오 인증에서 신규로 판정되면 계정을 만들지 않고 이 토큰을 내려주고,
 * 프론트가 약관 동의 후 POST /v1/auth/kakao/signup 에 그대로 보낸다. 서버 상태 저장 없음.
 * 액세스 토큰과 같은 키로 서명하되 typ 클레임으로 구분한다 — 여기서는 typ이 없으면 거부하고,
 * JwtProvider 쪽은 userId 클레임이 없으면 거부하므로 서로 바꿔 쓸 수 없다 (auth-계획 D-1).
 */
@Component
public class KakaoSignupTokenProvider {

    static final String TYPE_CLAIM = "typ";
    static final String TYPE_VALUE = "kakao_signup";
    static final String KAKAO_ID_CLAIM = "kakaoId";
    static final String NICKNAME_CLAIM = "kakaoNickname";
    private static final Duration DEFAULT_EXPIRY = Duration.ofMinutes(10);

    public record SignupClaims(String kakaoId, String kakaoNickname) {
    }

    private final SecretKey key;
    private final String issuer;
    private final Duration expiry;

    @Autowired
    public KakaoSignupTokenProvider(JwtProperties jwtProperties) {
        this(jwtProperties, DEFAULT_EXPIRY);
    }

    // 테스트용: 만료 시간을 짧게
    KakaoSignupTokenProvider(JwtProperties jwtProperties, Duration expiry) {
        this.key = Keys.hmacShaKeyFor(jwtProperties.secretKey().getBytes(StandardCharsets.UTF_8));
        this.issuer = jwtProperties.issuer();
        this.expiry = expiry;
    }

    public String issue(String kakaoId, String kakaoNickname) {
        Date now = new Date();
        return Jwts.builder()
                .issuer(issuer)
                .claim(TYPE_CLAIM, TYPE_VALUE)
                .claim(KAKAO_ID_CLAIM, kakaoId)
                .claim(NICKNAME_CLAIM, kakaoNickname)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiry.toMillis()))
                .signWith(key)
                .compact();
    }

    /** 만료·서명 불일치·typ 불일치·kakaoId 누락은 전부 401 INVALID_SIGNUP_TOKEN. */
    public SignupClaims parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (!TYPE_VALUE.equals(claims.get(TYPE_CLAIM, String.class))) {
                throw new BusinessException(ErrorCode.INVALID_SIGNUP_TOKEN);
            }
            String kakaoId = claims.get(KAKAO_ID_CLAIM, String.class);
            if (kakaoId == null || kakaoId.isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_SIGNUP_TOKEN);
            }
            return new SignupClaims(kakaoId, claims.get(NICKNAME_CLAIM, String.class));
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_SIGNUP_TOKEN);
        }
    }
}
