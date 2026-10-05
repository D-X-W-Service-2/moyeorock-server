package com.moyeorock.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import com.moyeorock.domain.user.enums.LoginType;
import com.moyeorock.domain.user.enums.UserStatus;
import com.moyeorock.domain.user.repository.UserInstrumentRepository;
import com.moyeorock.domain.user.repository.UserRepository;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long USER_ID = 12L;

    @Mock
    UserRepository userRepository;
    @Mock
    UserInstrumentRepository userInstrumentRepository;
    @InjectMocks
    UserService userService;

    private User activeUser(String nickname) {
        User user = newUser(nickname);
        user.updateProfile(nickname, Region.SEOUL, List.of(Genre.ROCK), null, null, true, true);
        return user;
    }

    private User newUser(String nickname) {
        User user = User.signupWithEmail("seojun@example.com", "hash", nickname, LocalDateTime.now());
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    private UserInstrument instrument(Long id, Instrument instrument, Level level) {
        UserInstrument entity = UserInstrument.create(USER_ID, instrument, level);
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private void givenActive(User user) {
        given(userRepository.findByIdAndStatus(USER_ID, UserStatus.ACTIVE)).willReturn(Optional.of(user));
    }

    private void givenActiveForUpdate(User user) {
        given(userRepository.findByIdAndStatusForUpdate(USER_ID, UserStatus.ACTIVE)).willReturn(Optional.of(user));
    }

    private static void assertErrorCode(Throwable thrown, ErrorCode expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }

    @Nested
    @DisplayName("getMe")
    class GetMe {

        @Test
        @DisplayName("활성 사용자의 프로필과 세션 목록을 돌려준다")
        void returns_profile_with_instruments() {
            User user = activeUser("서준");
            givenActive(user);
            given(userInstrumentRepository.findAllByUserIdOrderByIdAsc(USER_ID))
                    .willReturn(List.of(instrument(3L, Instrument.BASS, Level.INTERMEDIATE)));

            UserMeResponse response = userService.getMe(USER_ID);

            assertThat(response.id()).isEqualTo(USER_ID);
            assertThat(response.nickname()).isEqualTo("서준");
            assertThat(response.loginType()).isEqualTo(LoginType.EMAIL);
            assertThat(response.onboardingCompleted()).isFalse();
            assertThat(response.instruments()).hasSize(1);
            assertThat(response.instruments().get(0).instrument()).isEqualTo(Instrument.BASS);
        }

        @Test
        @DisplayName("활성 사용자가 아니면 USER_NOT_FOUND")
        void not_active_throws() {
            given(userRepository.findByIdAndStatus(USER_ID, UserStatus.ACTIVE)).willReturn(Optional.empty());

            assertErrorCode(org.assertj.core.api.Assertions.catchThrowable(() -> userService.getMe(USER_ID)),
                    ErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("updateMe")
    class UpdateMe {

        private UserUpdateRequest request(String nickname) {
            return new UserUpdateRequest(nickname, Region.BUSAN, List.of(Genre.JAZZ), "소개", "https://img", true, false);
        }

        @Test
        @DisplayName("전체 필드를 덮어쓰고 수정 후 프로필을 돌려준다")
        void updates_all_fields() {
            User user = activeUser("서준");
            givenActive(user);
            given(userRepository.existsByNickname("소붕이밴드")).willReturn(false);
            given(userInstrumentRepository.findAllByUserIdOrderByIdAsc(USER_ID)).willReturn(List.of());

            UserMeResponse response = userService.updateMe(USER_ID, request("소붕이밴드"));

            assertThat(response.nickname()).isEqualTo("소붕이밴드");
            assertThat(response.region()).isEqualTo(Region.BUSAN);
            assertThat(response.genres()).containsExactly(Genre.JAZZ);
            assertThat(response.isActivityPublic()).isFalse();
            assertThat(user.getBio()).isEqualTo("소개");
        }

        @Test
        @DisplayName("본인 닉네임을 그대로 보내면 중복 검사를 건너뛴다")
        void same_nickname_skips_duplicate_check() {
            User user = activeUser("서준");
            givenActive(user);
            given(userInstrumentRepository.findAllByUserIdOrderByIdAsc(USER_ID)).willReturn(List.of());

            userService.updateMe(USER_ID, request("서준"));

            verify(userRepository, never()).existsByNickname(any());
        }

        @Test
        @DisplayName("다른 사람이 쓰는 닉네임이면 NICKNAME_DUPLICATED")
        void duplicated_nickname_throws() {
            givenActive(activeUser("서준"));
            given(userRepository.existsByNickname("민서")).willReturn(true);

            assertErrorCode(org.assertj.core.api.Assertions.catchThrowable(
                    () -> userService.updateMe(USER_ID, request("민서"))), ErrorCode.NICKNAME_DUPLICATED);
        }

        @Test
        @DisplayName("'탈퇴회원' 접두사 닉네임은 VALIDATION_FAILED")
        void reserved_prefix_throws() {
            givenActive(activeUser("서준"));

            assertErrorCode(org.assertj.core.api.Assertions.catchThrowable(
                    () -> userService.updateMe(USER_ID, request("탈퇴회원_1"))), ErrorCode.VALIDATION_FAILED);
        }
    }

    @Nested
    @DisplayName("withdraw")
    class Withdraw {

        @Test
        @DisplayName("상태를 WITHDRAWN으로 바꾸고 탈퇴 시각을 돌려준다")
        void withdraws() {
            User user = activeUser("서준");
            givenActive(user);

            UserWithdrawResponse response = userService.withdraw(USER_ID);

            assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
            assertThat(user.getEmail()).isNull();
            assertThat(user.getNickname()).isEqualTo("탈퇴회원_" + USER_ID);
            assertThat(response.withdrawnAt()).isEqualTo(user.getWithdrawnAt());
        }

        @Test
        @DisplayName("이미 탈퇴한 사용자는 활성 조회에 실패해 USER_NOT_FOUND")
        void already_withdrawn_throws() {
            given(userRepository.findByIdAndStatus(USER_ID, UserStatus.ACTIVE)).willReturn(Optional.empty());

            assertErrorCode(org.assertj.core.api.Assertions.catchThrowable(() -> userService.withdraw(USER_ID)),
                    ErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("completeOnboarding")
    class CompleteOnboarding {

        private OnboardingCreateRequest request(String nickname, UserInstrumentRequest... instruments) {
            return new OnboardingCreateRequest(nickname, Region.SEOUL, List.of(Genre.ROCK, Genre.INDIE),
                    List.of(instruments));
        }

        @Test
        @DisplayName("잠금 조회 후 프로필을 채우고 세션을 전체 교체한다")
        void completes_onboarding() {
            User user = newUser("임시");
            givenActiveForUpdate(user);
            given(userRepository.existsByNickname("서준")).willReturn(false);
            given(userInstrumentRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

            UserMeResponse response = userService.completeOnboarding(USER_ID, request("서준",
                    new UserInstrumentRequest(Instrument.BASS, Level.INTERMEDIATE),
                    new UserInstrumentRequest(Instrument.KEY, Level.NOVICE)));

            verify(userInstrumentRepository).deleteAllByUserId(USER_ID);
            assertThat(user.isOnboardingCompleted()).isTrue();
            assertThat(response.onboardingCompleted()).isTrue();
            assertThat(response.nickname()).isEqualTo("서준");
            assertThat(response.instruments()).extracting(r -> r.instrument())
                    .containsExactly(Instrument.BASS, Instrument.KEY);
        }

        @Test
        @DisplayName("재호출해도 거부하지 않고 값만 갱신하며 완료 시각은 유지된다")
        void recall_is_idempotent() {
            User user = newUser("서준");
            user.completeOnboarding("서준", Region.SEOUL, List.of(Genre.ROCK));
            LocalDateTime first = user.getOnboardingCompletedAt();
            givenActiveForUpdate(user);
            given(userInstrumentRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

            userService.completeOnboarding(USER_ID, request("서준",
                    new UserInstrumentRequest(Instrument.DRUM, Level.ADVANCED)));

            assertThat(user.getOnboardingCompletedAt()).isEqualTo(first);
            verify(userRepository, never()).existsByNickname(any());
        }

        @Test
        @DisplayName("한 요청에 같은 세션이 두 번 오면 VALIDATION_FAILED")
        void duplicate_instrument_throws() {
            givenActiveForUpdate(activeUser("서준"));

            assertErrorCode(org.assertj.core.api.Assertions.catchThrowable(
                    () -> userService.completeOnboarding(USER_ID, request("서준",
                            new UserInstrumentRequest(Instrument.BASS, Level.NOVICE),
                            new UserInstrumentRequest(Instrument.BASS, Level.ADVANCED)))),
                    ErrorCode.VALIDATION_FAILED);
            verify(userInstrumentRepository, never()).deleteAllByUserId(any());
        }

        @Test
        @DisplayName("다른 사람이 쓰는 닉네임이면 NICKNAME_DUPLICATED")
        void duplicated_nickname_throws() {
            givenActiveForUpdate(activeUser("임시"));
            given(userRepository.existsByNickname("서준")).willReturn(true);

            assertErrorCode(org.assertj.core.api.Assertions.catchThrowable(
                    () -> userService.completeOnboarding(USER_ID, request("서준",
                            new UserInstrumentRequest(Instrument.BASS, Level.NOVICE)))),
                    ErrorCode.NICKNAME_DUPLICATED);
        }
    }

    @Nested
    @DisplayName("updateInstruments")
    class UpdateInstruments {

        @Test
        @DisplayName("기존 세션을 지우고 요청 순서대로 다시 넣는다")
        void replaces_all() {
            givenActiveForUpdate(activeUser("서준"));
            given(userInstrumentRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

            UserInstrumentsResponse response = userService.updateInstruments(USER_ID,
                    new UserInstrumentUpdateRequest(List.of(
                            new UserInstrumentRequest(Instrument.BASS, Level.ADVANCED),
                            new UserInstrumentRequest(Instrument.KEY, Level.NOVICE))));

            verify(userInstrumentRepository).deleteAllByUserId(USER_ID);
            assertThat(response.instruments()).extracting(r -> r.instrument())
                    .containsExactly(Instrument.BASS, Instrument.KEY);
            assertThat(response.instruments()).extracting(r -> r.level())
                    .containsExactly(Level.ADVANCED, Level.NOVICE);
        }

        @Test
        @DisplayName("한 요청에 같은 세션이 두 번 오면 VALIDATION_FAILED")
        void duplicate_instrument_throws() {
            givenActiveForUpdate(activeUser("서준"));

            assertErrorCode(org.assertj.core.api.Assertions.catchThrowable(
                    () -> userService.updateInstruments(USER_ID, new UserInstrumentUpdateRequest(List.of(
                            new UserInstrumentRequest(Instrument.KEY, Level.NOVICE),
                            new UserInstrumentRequest(Instrument.KEY, Level.ADVANCED))))),
                    ErrorCode.VALIDATION_FAILED);
        }

        @Test
        @DisplayName("활성 사용자가 아니면 USER_NOT_FOUND")
        void not_active_throws() {
            given(userRepository.findByIdAndStatusForUpdate(USER_ID, UserStatus.ACTIVE)).willReturn(Optional.empty());

            assertErrorCode(org.assertj.core.api.Assertions.catchThrowable(
                    () -> userService.updateInstruments(USER_ID, new UserInstrumentUpdateRequest(List.of(
                            new UserInstrumentRequest(Instrument.KEY, Level.NOVICE))))),
                    ErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("checkNickname")
    class CheckNickname {

        @Test
        @DisplayName("아무도 안 쓰면 available=true, 쓰고 있으면 false")
        void availability() {
            given(userRepository.existsByNickname("서준")).willReturn(true);
            given(userRepository.existsByNickname("새닉네임")).willReturn(false);

            NicknameCheckResponse taken = userService.checkNickname("서준");
            NicknameCheckResponse free = userService.checkNickname("새닉네임");

            assertThat(taken.available()).isFalse();
            assertThat(taken.nickname()).isEqualTo("서준");
            assertThat(free.available()).isTrue();
        }

        @Test
        @DisplayName("공백·20자 초과·예약 접두사는 VALIDATION_FAILED")
        void invalid_format_throws() {
            for (String invalid : List.of(" ", "가".repeat(21), "탈퇴회원_3")) {
                assertErrorCode(org.assertj.core.api.Assertions.catchThrowable(
                        () -> userService.checkNickname(invalid)), ErrorCode.VALIDATION_FAILED);
            }
            verify(userRepository, never()).existsByNickname(any());
        }
    }

    @Nested
    @DisplayName("search")
    class Search {

        @Test
        @DisplayName("ACTIVE만, 닉네임 오름차순 고정 정렬로 조회하고 요약 DTO로 매핑한다")
        void searches_with_fixed_sort() {
            User user = activeUser("서준");
            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            given(userRepository.findByStatusAndNicknameContaining(eq(UserStatus.ACTIVE), eq("서준"), pageableCaptor.capture()))
                    .willReturn(new PageImpl<>(List.of(user), PageRequest.of(0, 20), 1));

            PageResponse<UserSummaryResponse> response = userService.search("서준",
                    PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")));

            assertThat(pageableCaptor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "nickname"));
            assertThat(response.content()).extracting(UserSummaryResponse::nickname).containsExactly("서준");
            assertThat(response.totalElements()).isEqualTo(1);
            assertThat(response.last()).isTrue();
        }

        @Test
        @DisplayName("검색어가 비어 있으면 VALIDATION_FAILED (전체 회원 목록을 열지 않는다)")
        void blank_keyword_throws() {
            assertErrorCode(org.assertj.core.api.Assertions.catchThrowable(
                    () -> userService.search("  ", PageRequest.of(0, 20))), ErrorCode.VALIDATION_FAILED);
            verify(userRepository, never()).findByStatusAndNicknameContaining(any(), any(), any());
        }
    }
}
