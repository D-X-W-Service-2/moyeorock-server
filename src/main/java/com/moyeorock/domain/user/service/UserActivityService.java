package com.moyeorock.domain.user.service;

import com.moyeorock.domain.user.dto.response.UserProfileResponse;
import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.entity.UserInstrument;
import com.moyeorock.domain.user.enums.UserStatus;
import com.moyeorock.domain.user.repository.UserInstrumentRepository;
import com.moyeorock.domain.user.repository.UserRepository;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 활동·타인 프로필(api-spec §1의 Phase 2 2개, 이슈 #90). UserService에 두지 않는 이유: 2단계에서 2팀 TeamService와
 * 4팀 GroupService·PerformanceService를 주입받는데 그쪽이 이미 UserService를 주입받고 있어, UserService에 넣으면 생성자 순환 참조로
 * 컨텍스트가 뜨지 않는다(architecture.md §3). GroupMemberService·TeamMemberService처럼 도메인 안에서 나눈 Service이며,
 * 다른 도메인은 이 클래스를 주입받지 않는다. 다른 도메인의 Repository·Entity는 만지지 않고 Service만 부른다.
 *
 * <p>1단계(이 커밋)는 프로필 부분만이다. 활동 조립(getActivities)과 GET /v1/users/me/activities는 2팀·4팀 메서드가 main에 들어온 뒤 2단계에서 잇는다.
 */
@Service
@RequiredArgsConstructor
public class UserActivityService {

    private final UserRepository userRepository;
    private final UserInstrumentRepository userInstrumentRepository;

    /**
     * 타인 프로필. 탈퇴 회원과 없는 id는 404 USER_NOT_FOUND, 정지(SUSPENDED) 회원은 노출한다(P-7: getSummaries·filterByNickname이 정지 회원을
     * 목록에 보여주므로 프로필도 맞춘다). 본인 id로 불러도 같은 형태다(P-8). 사용자 + 세션 목록을 쿼리 2번으로 모은다 → 스냅샷 필요.
     * activities는 2단계 전까지 항상 null이다. 빈 목록 3개로 두면 "비공개"·"활동 없음"·"미구현"이 구분되지 않는다(P-3).
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long targetId) {
        User user = userRepository.findByIdAndStatusNot(targetId, UserStatus.WITHDRAWN)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<UserInstrument> instruments = userInstrumentRepository.findAllByUserIdOrderByIdAsc(targetId);
        return UserProfileResponse.of(user, instruments, null);
    }
}
