package com.moyeorock.domain.user.service;

import com.moyeorock.domain.group.dto.response.GroupBriefResponse;
import com.moyeorock.domain.group.service.GroupMemberService;
import com.moyeorock.domain.join.service.JoinRequestService;
import com.moyeorock.domain.team.dto.response.TeamSummaryResponse;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.domain.user.dto.response.UserWithdrawResponse;
import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.enums.UserStatus;
import com.moyeorock.domain.user.repository.UserRepository;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴(DELETE /v1/users/me). 직책 보유자는 탈퇴할 수 없다: 팀장은 위임 전, 모임장은 위임하거나 모임을 해체하기 전(10/7 결정, 10/30 재확인).
 * 팀 탈퇴(TeamService)·모임 탈퇴(GroupMemberService)는 각자 막고 있지만 회원 탈퇴는 users.status만 바꾸고 team_members·group_members를
 * 거치지 않아 그 규칙에 걸리지 않으므로, 여기서 두 도메인 Service에 직책을 묻는다.
 *
 * <p>UserService에 두지 않는 이유: TeamService가 UserService를 주입받고 있어 UserService가 그쪽을 주입받으면
 * 생성자 순환이 된다(architecture.md §3, UserActivityService와 같은 사정). 다른 도메인은 이 클래스를 주입받지 않는다.
 *
 * <p>대기 중인 가입 신청·초대는 사용자 행을 잠근 직후 닫고(3팀 cancelPendingOf), 직책 확인을 통과하면 모임원 행(4팀 leaveAllGroups, #112)과 팀원 행(2팀 leaveAllTeams, #97)을 전부 LEFT로 바꾼다. 그대로 두면 탈퇴 회원이
 * memberCount에는 세어지는데 목록에서는 빠져 둘이 어긋난다.
 */
@Service
@RequiredArgsConstructor
public class UserWithdrawService {

    private final UserRepository userRepository;
    private final TeamService teamService;
    private final GroupMemberService groupMemberService;
    private final JoinRequestService joinRequestService;

    /**
     * 사용자 행을 잠근 뒤 직책을 확인한다(잠금 없이 읽으면 동시 요청의 탈퇴가 커밋된 뒤 이 트랜잭션의 flush가 되돌린다, PR #57 리뷰).
     * 팀장인 활동 중인 팀이나 모임장인 활동 중인 모임이 하나라도 있으면 409 WITHDRAW_BLOCKED_BY_ROLE이고, 어느 팀·모임인지 message에 담는다.
     * 판정 뒤 위임·합류가 끼어드는 경우는 leaveAllGroups·leaveAllTeams가 각각 모임 행·팀 행을 잠그고 다시 확인해
     * OWNER_CANNOT_LEAVE·LEADER_MUST_DELEGATE_FIRST로 막고, 트랜잭션 전체가 롤백된다.
     */
    @Transactional
    public UserWithdrawResponse withdraw(Long userId) {
        User user = userRepository.findByIdAndStatusForUpdate(userId, UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        // 사용자 행 잠금 바로 다음, 직책 확인(일반 조회) 앞에서 부른다. 승인이 신청 행을 먼저 잠그고 있으면 여기서 기다렸다가
        // 커밋된 뒤에 아래 조회를 시작하므로, 승인이 만든 새 팀원·모임원 행을 leaveAllTeams·leaveAllGroups가 놓치지 않는다
        // (순서가 반대면 findLedTeams가 먼저 읽기 화면을 고정해 그 행이 안 보이고 탈퇴자가 ACTIVE로 남는다, PR #140 리뷰).
        // 직책 때문에 막히면 예외로 트랜잭션이 롤백되어 닫은 신청·초대도 되돌아간다.
        joinRequestService.cancelPendingOf(userId);
        List<TeamSummaryResponse> ledTeams = teamService.findLedTeams(userId);
        List<GroupBriefResponse> ownedGroups = groupMemberService.findOwnedGroups(userId);
        if (!ledTeams.isEmpty() || !ownedGroups.isEmpty()) {
            throw new BusinessException(ErrorCode.WITHDRAW_BLOCKED_BY_ROLE, blockedMessage(ledTeams, ownedGroups));
        }
        groupMemberService.leaveAllGroups(userId);
        teamService.leaveAllTeams(userId);
        user.withdraw();
        return new UserWithdrawResponse(user.getWithdrawnAt());
    }

    /**
     * 예: 팀 '무명밴드'의 팀장을 위임한 뒤 탈퇴할 수 있습니다.
     * 예: 팀 '무명밴드'의 팀장을 위임하고, 모임 '홍대 밴드부'의 모임장을 위임하거나 모임을 해체한 뒤 탈퇴할 수 있습니다.
     */
    private static String blockedMessage(List<TeamSummaryResponse> ledTeams, List<GroupBriefResponse> ownedGroups) {
        List<String> parts = new ArrayList<>();
        if (!ledTeams.isEmpty()) {
            parts.add("팀 " + quoted(ledTeams.stream().map(TeamSummaryResponse::name).toList()) + "의 팀장을 위임");
        }
        if (!ownedGroups.isEmpty()) {
            parts.add("모임 " + quoted(ownedGroups.stream().map(GroupBriefResponse::name).toList())
                    + "의 모임장을 위임하거나 모임을 해체");
        }
        return String.join("하고, ", parts) + "한 뒤 탈퇴할 수 있습니다.";
    }

    private static String quoted(List<String> names) {
        return names.stream().map(n -> "'" + n + "'").collect(Collectors.joining(", "));
    }
}
