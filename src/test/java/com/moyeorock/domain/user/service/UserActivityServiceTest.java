package com.moyeorock.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.moyeorock.domain.user.dto.response.UserProfileResponse;
import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.entity.UserInstrument;
import com.moyeorock.domain.user.enums.UserStatus;
import com.moyeorock.domain.user.repository.UserInstrumentRepository;
import com.moyeorock.domain.user.repository.UserRepository;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserActivityServiceTest {

    private static final Long TARGET_ID = 30L;

    @Mock
    UserRepository userRepository;
    @Mock
    UserInstrumentRepository userInstrumentRepository;
    @InjectMocks
    UserActivityService userActivityService;

    private User user(String nickname, boolean activityPublic) {
        User user = User.signupWithEmail("minseo@example.com", "hash", nickname, LocalDateTime.now());
        ReflectionTestUtils.setField(user, "id", TARGET_ID);
        user.updateProfile(nickname, Region.GYEONGGI, List.of(Genre.JAZZ), "드럼 칩니다", "https://img/c.jpg", true,
                activityPublic);
        return user;
    }

    private UserInstrument instrument(Long id, Instrument instrument, Level level) {
        UserInstrument entity = UserInstrument.create(TARGET_ID, instrument, level);
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private void givenNotWithdrawn(User user) {
        given(userRepository.findByIdAndStatusNot(TARGET_ID, UserStatus.WITHDRAWN)).willReturn(Optional.of(user));
    }

    @Nested
    @DisplayName("getProfile")
    class GetProfile {

        @Test
        @DisplayName("프로필과 세션(id 없이 instrument·level만)을 돌려주고, 본인 전용 필드는 없으며 activities는 2단계 전이라 null이다")
        void returns_profile_without_private_fields() {
            givenNotWithdrawn(user("민서", true));
            given(userInstrumentRepository.findAllByUserIdOrderByIdAsc(TARGET_ID))
                    .willReturn(List.of(instrument(8L, Instrument.DRUM, Level.ADVANCED)));

            UserProfileResponse response = userActivityService.getProfile(TARGET_ID);

            assertThat(response.id()).isEqualTo(TARGET_ID);
            assertThat(response.nickname()).isEqualTo("민서");
            assertThat(response.region()).isEqualTo(Region.GYEONGGI);
            assertThat(response.genres()).containsExactly(Genre.JAZZ);
            assertThat(response.bio()).isEqualTo("드럼 칩니다");
            assertThat(response.profileImage()).isEqualTo("https://img/c.jpg");
            assertThat(response.instruments())
                    .containsExactly(new UserProfileResponse.ProfileInstrument(Instrument.DRUM, Level.ADVANCED));
            assertThat(response.isActivityPublic()).isTrue();
            assertThat(response.activities()).isNull();
        }

        @Test
        @DisplayName("활동 비공개 사용자는 isActivityPublic=false로 내려가고 activities는 null이다")
        void private_user_exposes_flag_and_null_activities() {
            givenNotWithdrawn(user("민서", false));
            given(userInstrumentRepository.findAllByUserIdOrderByIdAsc(TARGET_ID)).willReturn(List.of());

            UserProfileResponse response = userActivityService.getProfile(TARGET_ID);

            assertThat(response.isActivityPublic()).isFalse();
            assertThat(response.activities()).isNull();
            assertThat(response.instruments()).isEmpty();
        }

        @Test
        @DisplayName("정지(SUSPENDED) 회원도 조회된다. 탈퇴만 제외하는 조회를 쓴다")
        void suspended_user_is_visible() {
            User suspended = user("현빈", true);
            ReflectionTestUtils.setField(suspended, "status", UserStatus.SUSPENDED);
            givenNotWithdrawn(suspended);
            given(userInstrumentRepository.findAllByUserIdOrderByIdAsc(TARGET_ID)).willReturn(List.of());

            UserProfileResponse response = userActivityService.getProfile(TARGET_ID);

            assertThat(response.nickname()).isEqualTo("현빈");
            verify(userRepository).findByIdAndStatusNot(TARGET_ID, UserStatus.WITHDRAWN);
        }

        @Test
        @DisplayName("탈퇴했거나 없는 사용자는 USER_NOT_FOUND다")
        void withdrawn_or_unknown_user_throws_not_found() {
            given(userRepository.findByIdAndStatusNot(TARGET_ID, UserStatus.WITHDRAWN)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userActivityService.getProfile(TARGET_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }
}
