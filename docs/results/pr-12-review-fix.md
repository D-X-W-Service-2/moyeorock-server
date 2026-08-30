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

## 3. `@AuthUser`가 인증 안 된 상태에서 쓰이면 원인 불명의 500이 나던 문제

**문제**: `AuthUserArgumentResolver`가 `SecurityContextHolder`에서 꺼낸 인증 정보의 principal을 검증 없이 곧바로 `Long`으로 반환하고 있었다. 보호된 API(로그인 필요)에서는 이 시점에 항상 진짜 로그인한 유저의 `Long` 값이 들어있어서 문제가 없지만, 로그인 안 해도 되는 공개 API(`/v0/auth/**` 등)에서 실수로 `@AuthUser`를 쓰면 인증 정보가 "익명 사용자"(문자열)라서 `Long`으로 바꾸는 과정에서 예외가 터진다. 이 예외는 원인을 짐작하기 어려운 500(서버 오류)으로만 응답에 나타난다.

**왜 지금 짚었나**: 로그인(`domain/auth`)을 아직 안 만들었는데, 토큰 재발급(refresh)처럼 "로그인은 필요 없지만 사용자 식별은 필요한" API를 만들 때 이 조합(공개 API + `@AuthUser`)에 걸리기 딱 좋은 상황이다. 미리 안전장치를 걸어둔다.

**수정**: 인증 정보가 없거나 principal이 `Long`이 아니면 `BusinessException(ErrorCode.UNAUTHORIZED)`을 던지도록 변경 — 이러면 원인 모를 500 대신 명확한 401 응답이 나간다.
(`src/main/java/com/moyeorock/global/security/AuthUserArgumentResolver.java`)

**재발 방지**: `AuthUserArgumentResolverTest` 추가 — 정상 인증 시 `userId` 반환, 인증 정보 없음/익명 사용자일 때 `BusinessException(UNAUTHORIZED)`을 던지는지 확인한다.

## 결과

- `./gradlew test` 통과 확인 (`JwtProviderTest` 4건, `JwtAuthFilterTest` 2건, 신규 `AuthUserArgumentResolverTest` 3건 모두 성공)
- 1·2·3번 모두 실제 로그인 기능(`domain/auth`)이 붙기 전에 미리 고쳐둔 것 — 지금 당장 눈에 보이는 장애는 없었지만, 나중에 로그인 기능을 붙이는 순간 터졌을 문제였다.
- 점검 중 함께 발견한 이슈(`git-convention.md`가 요구하는 `ModularityTests.verify()` 부재)는 아직 손대지 않기로 하고 `docs/plans/modularity-tests-gap.md`에 별도로 기록해뒀다.
