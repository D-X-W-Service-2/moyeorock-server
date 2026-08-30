# PR #12 리뷰 반영 결과

`docs/plans/pr-12-review-response.md`에서 정리한 리뷰 코멘트 2건을 실제로 고친 기록.

## 1. 유효한 토큰인데도 401이 나던 문제

**문제**: 로그인 토큰이 정상이어도 보호된 API를 호출하면 무조건 401(인증 실패)이 떨어지는 버그가 있었다.

**원인**: `UserAuthentication` 객체를 만들 때 "이 사용자는 인증됐다"는 표시(`authenticated = true`)를 켜주지 않아서, Spring Security가 기본값인 "미인증"으로 계속 판단했다.

**수정**: `UserAuthentication` 생성자에서 `setAuthenticated(true)`를 호출하도록 한 줄 추가.
(`src/main/java/com/moyeorock/global/security/UserAuthentication.java`)

**재발 방지**: 이 버그를 그대로 재현하는 테스트(`JwtAuthFilterTest`)를 추가했다. 유효한 토큰을 넣었을 때 인증 상태가 `true`로 설정되는지, 잘못된 토큰을 넣었을 때 인증 정보가 비워지는지를 확인한다. 앞으로 같은 실수를 하면 이 테스트가 바로 실패한다.

## 2. 로그인 API 경로가 잘못 설정돼 있던 문제

**문제**: 로그인처럼 "로그인 안 해도 되는" API 목록에 `/api/auth/**`가 등록돼 있었는데, 실제 API 주소 규칙은 `/v0/auth/**`다. 이대로 두면 나중에 로그인 API를 만들었을 때 "로그인하려면 로그인이 먼저 돼있어야 하는" 모순에 빠져 로그인 자체가 막힌다.

**수정**: `PublicEndpoints.AUTH` 경로를 `/api/auth/**` → `/v0/auth/**`로 수정.
(`src/main/java/com/moyeorock/global/security/PublicEndpoints.java`)

## 결과

- `./gradlew test` 통과 확인 (`JwtProviderTest` 4건, 신규 `JwtAuthFilterTest` 2건 모두 성공)
- 두 문제 모두 실제 로그인 기능(`domain/auth`)이 붙기 전에 미리 고쳐둔 것 — 지금 당장 눈에 보이는 장애는 없었지만, 나중에 로그인 기능을 붙이는 순간 터졌을 문제였다.
