package com.moyeorock.domain.rehearsal.service;

import com.moyeorock.domain.rehearsal.dto.response.RehearsalDetailResponse;
import com.moyeorock.domain.rehearsal.dto.response.RehearsalSummaryResponse;
import com.moyeorock.domain.rehearsal.dto.response.RehearsalsResponse;
import com.moyeorock.domain.rehearsal.entity.Rehearsal;
import com.moyeorock.domain.rehearsal.repository.RehearsalRepository;
import com.moyeorock.domain.team.dto.response.TeamMembershipResponse;
import com.moyeorock.domain.team.service.TeamMemberService;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import com.moyeorock.domain.user.service.UserService;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 다른 도메인 Service(팀·사용자)를 부르는 조회가 많아서 트랜잭션을 붙이지 않는다(conventions.md §3).
 * 호출된 Service가 각자 트랜잭션을 열어 바깥에서 감싸도 같은 시점이 보장되지 않기 때문이다.
 */
@Service
@RequiredArgsConstructor
public class RehearsalService {

    private final RehearsalRepository rehearsalRepository;
    private final TeamMemberService teamMemberService;
    private final TeamService teamService;
    private final UserService userService;

    /** 팀 합주 목록. 그 팀의 활동 중 팀원만 볼 수 있고, 없는·해체된 팀은 TEAM_NOT_FOUND다. */
    public RehearsalsResponse getTeamRehearsals(Long userId, Long teamId, LocalDate from, LocalDate to) {
        Period period = resolvePeriod(from, to);
        teamMemberService.ensureActiveMember(teamId, userId);

        List<Rehearsal> rehearsals = rehearsalRepository.findInPeriod(teamId, period.start(), period.end());
        String teamName = teamMemberService.getNames(List.of(teamId)).get(teamId);
        return new RehearsalsResponse(rehearsals.stream()
                .map(rehearsal -> RehearsalSummaryResponse.of(rehearsal, teamName))
                .toList());
    }

    /**
     * 내 합주 일정. 내가 활동 중인 팀의 합주를 모아 돌려준다.
     * 해체된 팀은 findMemberships에서 이미 빠지므로 합주 쪽에서 따로 거르지 않는다(architecture.md §6).
     */
    public RehearsalsResponse getMyRehearsals(Long userId, LocalDate from, LocalDate to) {
        Period period = resolvePeriod(from, to);

        Map<Long, String> teamNames = teamService.findMemberships(userId).stream()
                .map(TeamMembershipResponse::team)
                .collect(Collectors.toMap(team -> team.id(), team -> team.name()));
        if (teamNames.isEmpty()) {
            return new RehearsalsResponse(List.of());
        }

        List<Rehearsal> rehearsals = rehearsalRepository
                .findInPeriodByTeamIds(teamNames.keySet(), period.start(), period.end());
        return new RehearsalsResponse(rehearsals.stream()
                .map(rehearsal -> RehearsalSummaryResponse.of(rehearsal, teamNames.get(rehearsal.getTeamId())))
                .toList());
    }

    /** 합주 상세. 그 팀의 활동 중 팀원만 볼 수 있다. */
    public RehearsalDetailResponse getDetail(Long userId, Long rehearsalId) {
        Rehearsal rehearsal = getRehearsal(rehearsalId);
        teamMemberService.ensureActiveMember(rehearsal.getTeamId(), userId);
        return toDetail(rehearsal, userId);
    }

    private Rehearsal getRehearsal(Long rehearsalId) {
        return rehearsalRepository.findById(rehearsalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REHEARSAL_NOT_FOUND));
    }

    // 수정·삭제할 수 있는 사람은 작성자나 팀장이다. 팀원인지는 이 메서드를 부르기 전에 확인한다.
    private boolean canEdit(Rehearsal rehearsal, Long userId) {
        return rehearsal.isCreator(userId) || teamMemberService.isLeader(rehearsal.getTeamId(), userId);
    }

    // 탈퇴한 작성자는 getSummaries 결과에 없어 createdBy가 null로 내려간다
    private RehearsalDetailResponse toDetail(Rehearsal rehearsal, Long viewerId) {
        Long teamId = rehearsal.getTeamId();
        String teamName = teamMemberService.getNames(List.of(teamId)).get(teamId);
        UserSummaryResponse creator = userService.getSummaries(List.of(rehearsal.getCreatedBy()))
                .get(rehearsal.getCreatedBy());
        return RehearsalDetailResponse.of(rehearsal, teamName, creator, canEdit(rehearsal, viewerId));
    }

    /**
     * from·to(날짜, 둘 다 포함)를 Repository가 쓰는 [시작, 끝) 시각 범위로 바꾼다.
     * 둘 다 생략하면 이번 달 한 달치다. 하나만 보내거나, to가 from보다 앞서거나, 3달을 넘으면 VALIDATION_FAILED다.
     * to는 그 날을 포함해야 하므로 끝은 to의 다음 날 0시(미만)로 잡는다.
     */
    private Period resolvePeriod(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            YearMonth thisMonth = YearMonth.now();
            from = thisMonth.atDay(1);
            to = thisMonth.atEndOfMonth();
        }
        if (from == null || to == null || to.isBefore(from) || to.isAfter(from.plusMonths(3))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return new Period(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
    }

    // 조회 기간 [start, end). 이 클래스 안에서만 쓰는 값 묶음이라 중첩 record로 둔다.
    private record Period(LocalDateTime start, LocalDateTime end) {
    }
}
