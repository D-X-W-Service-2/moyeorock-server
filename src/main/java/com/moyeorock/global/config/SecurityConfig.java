package com.moyeorock.global.config;

import com.moyeorock.global.security.InternalApiKeyFilter;
import com.moyeorock.global.security.JwtAuthFilter;
import com.moyeorock.global.security.JwtProvider;
import com.moyeorock.global.security.PublicEndpoints;
import com.moyeorock.global.security.RestAccessDeniedHandler;
import com.moyeorock.global.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    // 허용 origin 목록(쉼표 구분). 기본값은 application.yml, 운영 배포는 CORS_ALLOWED_ORIGINS로 목록 전체를 바꾼다(#123).
    // allowCredentials(true)라 "*"는 쓸 수 없고, 패턴(http://localhost:*)은 setAllowedOriginPatterns로 받는다.
    @Value("${cors.allowed-origins}")
    private String[] allowedOrigins;

    // 내부 서버(AI 서버) 인증 키. 비어 있거나 32바이트보다 짧으면 /v1/internal/** 요청을 모두 거절한다(InternalApiKeyFilter, 이슈 #147).
    @Value("${internal.api-key:}")
    private String internalApiKey;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 내부 서버가 부르는 {@code /v1/internal/**} 전용 체인 — 아래 기본 체인보다 먼저 본다. 사용자 JWT 필터를 걸지 않아
     * 로그인 토큰으로는 통과할 수 없고, {@code X-Internal-Api-Key}가 맞을 때만 들어온다. 서버끼리 부르는 API라
     * 브라우저용 CORS는 열지 않는다. 401·403 응답 모양은 기본 체인과 같다.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain internalSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(InternalApiKeyFilter.PATH_PATTERN)
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .addFilterBefore(new InternalApiKeyFilter(internalApiKey), AuthorizationFilter.class)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler)
                );

        return http.build();
    }

    // 기본 체인(사용자 JWT). @Order가 없어 가장 나중에 보므로 /v1/internal/**는 위 내부 체인이 먼저 가져간다.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PublicEndpoints.paths()).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthFilter(jwtProvider), AuthorizationFilter.class)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler)
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.stream(allowedOrigins)
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}