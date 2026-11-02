package com.moyeorock.domain.user.service;

import com.moyeorock.domain.team.dto.response.TeamSummaryResponse;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.domain.user.dto.response.UserWithdrawResponse;
import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.enums.UserStatus;
import com.moyeorock.domain.user.repository.UserRepository;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴(DELETE /v1/users/me). 직책 보유자는 탈퇴할 수 없다: 팀장은 위임 전, 모임장은 해체 전(10/7 결정, 10/30 재확인).
 * 팀 탈퇴(TeamService)·모임 탈퇴(GroupMemberService)는 각자 막고 있지만 회원 탈퇴는 users.status만 바꾸고 team_members·group_members를
 * 거치지 않아 그 규칙에 걸리지 않으므로, 여기서 두 도메인 Service에 직책을 묻는다.
 *
 * <p>UserService에 두지 않는 이유: TeamService·GroupMemberService가 UserService를 주입받고 있어 UserService가 그쪽을 주입받으면
 * 생성자 순환이 된다(architecture.md §3, UserActivityService와 같은 사정). 다른 도메인은 이 클래스를 주입받지 않는다.
 *
 * <p>모임 쪽 판정(OWNER인 ACTIVE 모임)과 탈퇴 시 멤버십 LEFT 전환은 4팀 후속 이슈의 GroupMemberService 메서드가 main에 들어오면 잇는다(PR #67 답글).
 */
@Service
@RequiredArgsConstructor
public class UserWithdrawService {

    private final UserRepository userRepository;
    private final TeamService teamService;

    /**
     * 사용자 행을 잠근 뒤 직책을 확인한다(잠금 없이 읽으면 동시 요청의 탈퇴가 커밋된 뒤 이 트랜잭션의 flush가 되돌린다, PR #57 리뷰).
     * 팀장인 활동 중인 팀이 하나라도 있으면 409 WITHDRAW_BLOCKED_BY_ROLE이고, 어느 팀인지 message에 담아 프론트가 그대로 안내할 수 있게 한다.
     * 팀 행은 잠그지 않으므로 판정과 탈퇴 사이에 위임이 끼어들 틈은 있다(다른 판정 메서드와 같은 수준).
     */
    @Transactional
    public UserWithdrawResponse withdraw(Long userId) {
        User user = userRepository.findByIdAndStatusForUpdate(userId, UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<TeamSummaryResponse> ledTeams = teamService.findLedTeams(userId);
        if (!ledTeams.isEmpty()) {
            throw new BusinessException(ErrorCode.WITHDRAW_BLOCKED_BY_ROLE, blockedByTeamsMessage(ledTeams));
        }
        user.withdraw();
        return new UserWithdrawResponse(user.getWithdrawnAt());
    }

    /** 예: 팀 '무명밴드', '두번째'의 팀장을 위임한 뒤 탈퇴할 수 있습니다. */
    private static String blockedByTeamsMessage(List<TeamSummaryResponse> ledTeams) {
        String names = ledTeams.stream().map(t -> "'" + t.name() + "'").collect(Collectors.joining(", "));
        return "팀 " + names + "의 팀장을 위임한 뒤 탈퇴할 수 있습니다.";
    }
}
