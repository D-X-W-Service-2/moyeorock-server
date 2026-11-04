package com.moyeorock.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.moyeorock.domain.group.dto.response.GroupSummaryResponse;
import com.moyeorock.domain.group.enums.GroupRole;
import com.moyeorock.domain.group.enums.GroupType;
import com.moyeorock.domain.group.service.GroupMemberService;
import com.moyeorock.domain.performance.dto.response.PerformanceActivityResponse;
import com.moyeorock.domain.performance.enums.PerformanceStatus;
import com.moyeorock.domain.performance.service.PerformanceService;
import com.moyeorock.domain.team.dto.response.TeamMembershipResponse;
import com.moyeorock.domain.team.dto.response.TeamSummaryResponse;
import com.moyeorock.domain.team.enums.TeamRole;
import com.moyeorock.domain.team.enums.TeamStatus;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.domain.user.dto.response.UserActivityResponse;
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
    @Mock
    TeamService teamService;
    @Mock
    GroupMemberService groupMemberService;
    @Mock
    PerformanceService performanceService;
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

    private TeamMembershipResponse membership(Long teamId, String name, TeamRole role, Instrument instrument) {
        return new TeamMembershipResponse(
                new TeamSummaryResponse(teamId, name, Region.SEOUL, List.of(Genre.ROCK), TeamStatus.ACTIVE, 4),
                role, instrument);
    }

    private GroupSummaryResponse group(Long groupId, String name, GroupRole role) {
        return new GroupSummaryResponse(groupId, name, GroupType.REGULAR, role, null);
    }

    private PerformanceActivityResponse performance(Long performanceId, String title, String teamName) {
        return new PerformanceActivityResponse(performanceId, title, "홍대 밴드부", LocalDateTime.of(2026, 12, 20, 19, 0),
                PerformanceStatus.PLANNED, teamName);
    }

    private void givenNotWithdrawn(User user) {
        given(userRepository.findByIdAndStatusNot(TARGET_ID, UserStatus.WITHDRAWN)).willReturn(Optional.of(user));
    }

    @Nested
    @DisplayName("getProfile")
    class GetProfile {

        @Test
        @DisplayName("프로필과 세션(id 없이 instrument·level만)을 돌려주고, 본인 전용 필드는 없으며 공개 사용자는 activities에 팀·모임·공연 활동이 담긴다")
        void returns_profile_without_private_fields() {
            givenNotWithdrawn(user("민서", true));
            given(userInstrumentRepository.findAllByUserIdOrderByIdAsc(TARGET_ID))
                    .willReturn(List.of(instrument(8L, Instrument.DRUM, Level.ADVANCED)));
            TeamMembershipResponse membership = membership(3L, "무명밴드", TeamRole.MEMBER, Instrument.DRUM);
            given(teamService.findMemberships(TARGET_ID)).willReturn(List.of(membership));
            GroupSummaryResponse group = group(7L, "홍대 밴드부", GroupRole.MEMBER);
            given(groupMemberService.findMyGroupSummaries(TARGET_ID)).willReturn(List.of(group));
            PerformanceActivityResponse performance = performance(11L, "겨울 정기공연", "무명밴드");
            given(performanceService.findActivitiesByUserId(TARGET_ID)).willReturn(List.of(performance));

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
            assertThat(response.activities().teams()).containsExactly(membership);
            assertThat(response.activities().groups()).containsExactly(group);
            assertThat(response.activities().performances()).containsExactly(performance);
        }

        @Test
        @DisplayName("활동 비공개 사용자는 isActivityPublic=false로 내려가고 activities는 null이다. 활동 조회는 아예 하지 않는다")
        void private_user_exposes_flag_and_null_activities() {
            givenNotWithdrawn(user("민서", false));
            given(userInstrumentRepository.findAllByUserIdOrderByIdAsc(TARGET_ID)).willReturn(List.of());

            UserProfileResponse response = userActivityService.getProfile(TARGET_ID);

            assertThat(response.isActivityPublic()).isFalse();
            assertThat(response.activities()).isNull();
            assertThat(response.instruments()).isEmpty();
            verifyNoInteractions(teamService, groupMemberService, performanceService);
        }

        @Test
        @DisplayName("정지(SUSPENDED) 회원도 조회된다. 탈퇴만 제외하는 조회를 쓴다")
        void suspended_user_is_visible() {
            User suspended = user("현빈", true);
            ReflectionTestUtils.setField(suspended, "status", UserStatus.SUSPENDED);
            givenNotWithdrawn(suspended);
            given(userInstrumentRepository.findAllByUserIdOrderByIdAsc(TARGET_ID)).willReturn(List.of());
            given(teamService.findMemberships(TARGET_ID)).willReturn(List.of());

            UserProfileResponse response = userActivityService.getProfile(TARGET_ID);

            assertThat(response.nickname()).isEqualTo("현빈");
            assertThat(response.activities().teams()).isEmpty();
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

    @Nested
    @DisplayName("getActivities")
    class GetActivities {

        @Test
        @DisplayName("ACTIVE 사용자면 2팀이 준 팀 활동, 4팀이 준 모임·공연 활동을 순서 그대로 담는다")
        void returns_provided_lists_as_is() {
            given(userRepository.findByIdAndStatus(TARGET_ID, UserStatus.ACTIVE)).willReturn(Optional.of(user("민서", true)));
            List<TeamMembershipResponse> memberships = List.of(
                    membership(3L, "무명밴드", TeamRole.LEADER, Instrument.BASS),
                    membership(5L, "두번째", TeamRole.MEMBER, Instrument.DRUM));
            given(teamService.findMemberships(TARGET_ID)).willReturn(memberships);
            List<GroupSummaryResponse> groups = List.of(
                    group(7L, "홍대 밴드부", GroupRole.OWNER),
                    group(9L, "주말 합주", GroupRole.MEMBER));
            given(groupMemberService.findMyGroupSummaries(TARGET_ID)).willReturn(groups);
            // 한 공연에 내 팀이 둘이면 팀마다 한 줄(4팀 규칙). user는 묶지 않고 그대로 내린다
            List<PerformanceActivityResponse> performances = List.of(
                    performance(11L, "겨울 정기공연", "무명밴드"),
                    performance(11L, "겨울 정기공연", "두번째"));
            given(performanceService.findActivitiesByUserId(TARGET_ID)).willReturn(performances);

            UserActivityResponse response = userActivityService.getActivities(TARGET_ID);

            assertThat(response.teams()).containsExactlyElementsOf(memberships);
            assertThat(response.groups()).containsExactlyElementsOf(groups);
            assertThat(response.performances()).containsExactlyElementsOf(performances);
        }

        @Test
        @DisplayName("비공개 설정이어도 본인의 내 활동은 조회된다(비공개는 타인 프로필에만 적용)")
        void own_activities_ignore_private_flag() {
            given(userRepository.findByIdAndStatus(TARGET_ID, UserStatus.ACTIVE)).willReturn(Optional.of(user("민서", false)));
            given(teamService.findMemberships(TARGET_ID)).willReturn(List.of());

            UserActivityResponse response = userActivityService.getActivities(TARGET_ID);

            assertThat(response.teams()).isEmpty();
        }

        @Test
        @DisplayName("ACTIVE가 아닌(탈퇴·정지) 사용자나 없는 id는 다른 /me 엔드포인트와 같이 USER_NOT_FOUND다")
        void non_active_user_throws_not_found() {
            given(userRepository.findByIdAndStatus(TARGET_ID, UserStatus.ACTIVE)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userActivityService.getActivities(TARGET_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
            verifyNoInteractions(teamService, groupMemberService, performanceService);
        }
    }
}
