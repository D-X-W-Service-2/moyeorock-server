package com.moyeorock.domain.user.service;

import com.moyeorock.domain.user.dto.request.OnboardingCreateRequest;
import com.moyeorock.domain.user.dto.request.UserInstrumentRequest;
import com.moyeorock.domain.user.dto.request.UserInstrumentUpdateRequest;
import com.moyeorock.domain.user.dto.request.UserUpdateRequest;
import com.moyeorock.domain.user.dto.response.NicknameCheckResponse;
import com.moyeorock.domain.user.dto.response.UserInstrumentsResponse;
import com.moyeorock.domain.user.dto.response.UserMeResponse;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import com.moyeorock.domain.user.dto.response.UserWithdrawResponse;
import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.entity.UserInstrument;
import com.moyeorock.domain.user.enums.UserStatus;
import com.moyeorock.domain.user.repository.UserInstrumentRepository;
import com.moyeorock.domain.user.repository.UserRepository;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    /** 사용자 검색 정렬은 닉네임 오름차순 고정. 클라이언트 sort 파라미터는 무시한다 (명세). */
    private static final Sort SEARCH_SORT = Sort.by(Sort.Direction.ASC, "nickname");

    private final UserRepository userRepository;
    private final UserInstrumentRepository userInstrumentRepository;

    public UserMeResponse getMe(Long userId) {
        User user = getActiveUser(userId);
        return UserMeResponse.from(user, findInstruments(userId));
    }

    @Transactional
    public UserMeResponse updateMe(Long userId, UserUpdateRequest request) {
        User user = getActiveUser(userId);
        validateNicknameFormat(request.nickname());
        ensureNicknameAvailable(user, request.nickname());
        user.updateProfile(request.nickname(), request.region(), request.genres(), request.bio(),
                request.profileImage(), request.isRecommendable(), request.isActivityPublic());
        return UserMeResponse.from(user, findInstruments(userId));
    }

    @Transactional
    public UserWithdrawResponse withdraw(Long userId) {
        User user = getActiveUser(userId);
        LocalDateTime now = LocalDateTime.now();
        user.withdraw(now);
        return new UserWithdrawResponse(now);
    }

    /** 재호출은 거부하지 않고 값만 갱신한다(멱등). 세션 교체가 UNIQUE에 걸리지 않도록 잠금 조회로 직렬화한다. */
    @Transactional
    public UserMeResponse completeOnboarding(Long userId, OnboardingCreateRequest request) {
        User user = getActiveUserForUpdate(userId);
        validateNicknameFormat(request.nickname());
        ensureNicknameAvailable(user, request.nickname());
        validateNoDuplicateInstrument(request.instruments());
        user.completeOnboarding(request.nickname(), request.region(), request.genres(), LocalDateTime.now());
        List<UserInstrument> instruments = replaceInstruments(userId, request.instruments());
        return UserMeResponse.from(user, instruments);
    }

    @Transactional
    public UserInstrumentsResponse updateInstruments(Long userId, UserInstrumentUpdateRequest request) {
        getActiveUserForUpdate(userId);
        validateNoDuplicateInstrument(request.instruments());
        return UserInstrumentsResponse.from(replaceInstruments(userId, request.instruments()));
    }

    /** 저장하지 않고 사용 가능 여부만. 탈퇴 사용자의 닉네임은 이미 "탈퇴회원_{id}"로 치환돼 있어 원래 닉네임은 풀려 있다. */
    public NicknameCheckResponse checkNickname(String nickname) {
        validateNicknameFormat(nickname);
        return new NicknameCheckResponse(nickname, !userRepository.existsByNickname(nickname));
    }

    /** ACTIVE 회원만, 닉네임 부분 일치. 검색어가 비면 400 (전체 회원 목록 조회 경로를 열지 않는다). */
    public PageResponse<UserSummaryResponse> search(String nickname, Pageable pageable) {
        if (nickname == null || nickname.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        Pageable fixedSort = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), SEARCH_SORT);
        return PageResponse.from(userRepository
                .findByStatusAndNicknameContaining(UserStatus.ACTIVE, nickname.trim(), fixedSort)
                .map(UserSummaryResponse::from));
    }

    private User getActiveUser(Long userId) {
        return userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private User getActiveUserForUpdate(Long userId) {
        return userRepository.findByIdAndStatusForUpdate(userId, UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private List<UserInstrument> findInstruments(Long userId) {
        return userInstrumentRepository.findAllByUserIdOrderByIdAsc(userId);
    }

    /** 본인 닉네임을 그대로 보내면 중복 검사를 건너뛴다. */
    private void ensureNicknameAvailable(User user, String nickname) {
        if (!nickname.equals(user.getNickname()) && userRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }
    }

    /** 공백·20자 초과·"탈퇴회원" 접두사(탈퇴 치환용 예약어)는 400. @Valid를 거치지 않는 쿼리 파라미터 경로도 같은 규칙. */
    private void validateNicknameFormat(String nickname) {
        if (nickname == null || nickname.isBlank()
                || nickname.length() > User.NICKNAME_MAX_LENGTH
                || nickname.startsWith(User.WITHDRAWN_NICKNAME_PREFIX)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }

    /** 한 요청 안의 같은 instrument는 서버 상태 충돌(409)이 아니라 입력 오류(400). 흘리면 UNIQUE 위반으로 500이 된다. */
    private void validateNoDuplicateInstrument(List<UserInstrumentRequest> instruments) {
        Set<Instrument> seen = new HashSet<>();
        for (UserInstrumentRequest item : instruments) {
            if (!seen.add(item.instrument())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
        }
    }

    /** 전체 교체: 벌크 DELETE(즉시 실행) 후 요청 순서대로 재삽입. id는 매번 새로 발급된다. */
    private List<UserInstrument> replaceInstruments(Long userId, List<UserInstrumentRequest> instruments) {
        userInstrumentRepository.deleteAllByUserId(userId);
        List<UserInstrument> entities = instruments.stream()
                .map(item -> UserInstrument.create(userId, item.instrument(), item.level()))
                .toList();
        return userInstrumentRepository.saveAll(entities);
    }
}
