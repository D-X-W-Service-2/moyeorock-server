package com.moyeorock.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.moyeorock.domain.team.dto.response.TeamSummaryResponse;
import com.moyeorock.domain.team.enums.TeamStatus;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.domain.user.dto.response.UserWithdrawResponse;
import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.enums.UserStatus;
import com.moyeorock.domain.user.repository.UserRepository;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserWithdrawServiceTest {

    private static final Long USER_ID = 12L;

    @Mock
    UserRepository userRepository;
    @Mock
    TeamService teamService;
    @InjectMocks
    UserWithdrawService userWithdrawService;

    private User activeUser() {
        User user = User.signupWithEmail("seojun@example.com", "hash", "서준", LocalDateTime.now());
        ReflectionTestUtils.setField(user, "id", USER_ID);
        user.updateProfile("서준", Region.SEOUL, List.of(Genre.ROCK), null, null, true, true);
        return user;
    }

    private static TeamSummaryResponse team(Long id, String name) {
        return new TeamSummaryResponse(id, name, Region.SEOUL, List.of(Genre.ROCK), TeamStatus.ACTIVE, 3);
    }

    private void givenActiveForUpdate(User user) {
        given(userRepository.findByIdAndStatusForUpdate(USER_ID, UserStatus.ACTIVE)).willReturn(Optional.of(user));
    }

    @Test
    @DisplayName("팀장인 팀이 없으면 상태를 WITHDRAWN으로 바꾸고 개인정보를 비우며 탈퇴 시각을 돌려준다")
    void withdraws_whenNotLeadingAnyTeam() {
        User user = activeUser();
        givenActiveForUpdate(user);
        given(teamService.findLedTeams(USER_ID)).willReturn(List.of());

        UserWithdrawResponse response = userWithdrawService.withdraw(USER_ID);

        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(user.getEmail()).isNull();
        assertThat(user.getNickname()).isEqualTo("탈퇴회원_" + USER_ID);
        assertThat(response.withdrawnAt()).isEqualTo(user.getWithdrawnAt());
    }

    @Test
    @DisplayName("팀장인 활동 중인 팀이 있으면 WITHDRAW_BLOCKED_BY_ROLE이고 메시지에 팀 이름이 전부 담기며 상태는 그대로다")
    void blocked_whenLeadingTeams() {
        User user = activeUser();
        givenActiveForUpdate(user);
        given(teamService.findLedTeams(USER_ID)).willReturn(List.of(team(3L, "무명밴드"), team(5L, "두번째")));

        Throwable thrown = catchThrowable(() -> userWithdrawService.withdraw(USER_ID));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(ErrorCode.WITHDRAW_BLOCKED_BY_ROLE);
        assertThat(thrown.getMessage()).isEqualTo("팀 '무명밴드', '두번째'의 팀장을 위임한 뒤 탈퇴할 수 있습니다.");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getEmail()).isEqualTo("seojun@example.com");
    }

    @Test
    @DisplayName("이미 탈퇴했거나 없는 사용자는 USER_NOT_FOUND이고 팀 조회는 하지 않는다")
    void already_withdrawn_throws_beforeTeamLookup() {
        given(userRepository.findByIdAndStatusForUpdate(USER_ID, UserStatus.ACTIVE)).willReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> userWithdrawService.withdraw(USER_ID));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
        verifyNoInteractions(teamService);
    }
}
