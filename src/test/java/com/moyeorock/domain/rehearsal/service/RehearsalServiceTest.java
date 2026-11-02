package com.moyeorock.domain.rehearsal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moyeorock.domain.rehearsal.dto.response.RehearsalDetailResponse;
import com.moyeorock.domain.rehearsal.dto.response.RehearsalsResponse;
import com.moyeorock.domain.rehearsal.entity.Rehearsal;
import com.moyeorock.domain.rehearsal.repository.RehearsalRepository;
import com.moyeorock.domain.team.dto.response.TeamMembershipResponse;
import com.moyeorock.domain.team.dto.response.TeamSummaryResponse;
import com.moyeorock.domain.team.enums.TeamRole;
import com.moyeorock.domain.team.enums.TeamStatus;
import com.moyeorock.domain.team.service.TeamMemberService;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import com.moyeorock.domain.user.service.UserService;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RehearsalServiceTest {

    private static final Long TEAM_ID = 5L;
    private static final Long CREATOR_ID = 10L;
    private static final Long LEADER_ID = 11L;
    private static final Long MEMBER_ID = 12L;
    private static final Long REHEARSAL_ID = 41L;
    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);

    @Mock
    RehearsalRepository rehearsalRepository;
    @Mock
    TeamMemberService teamMemberService;
    @Mock
    TeamService teamService;
    @Mock
    UserService userService;
    @InjectMocks
    RehearsalService rehearsalService;

    private Rehearsal rehearsal(Long id, Long teamId, String title) {
        Rehearsal rehearsal = Rehearsal.create(teamId, CREATOR_ID, title, LocalDateTime.of(2026, 9, 14, 19, 0),
                LocalDateTime.of(2026, 9, 14, 21, 0), "홍대", "메모");
        ReflectionTestUtils.setField(rehearsal, "id", id);
        return rehearsal;
    }

    private TeamMembershipResponse membership(Long teamId, String name) {
        TeamSummaryResponse team = new TeamSummaryResponse(teamId, name, Region.SEOUL, List.of(), TeamStatus.ACTIVE, 3);
        return new TeamMembershipResponse(team, TeamRole.MEMBER, Instrument.BASS);
    }

    private void givenNotMember(Long teamId, Long userId) {
        willThrow(new BusinessException(ErrorCode.NO_PERMISSION))
                .given(teamMemberService).ensureActiveMember(teamId, userId);
    }

    private static void assertErrorCode(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }

    // ---------- 팀 합주 목록 ----------

    @Test
    @DisplayName("팀 합주 목록: 팀원이면 to 다음 날 0시 미만까지의 합주를 팀 이름과 함께 돌려준다")
    void getTeamRehearsals_returnsRehearsalsInPeriodWithTeamName() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 1, 0, 0);
        given(rehearsalRepository.findInPeriod(TEAM_ID, start, end))
                .willReturn(List.of(rehearsal(1L, TEAM_ID, "1차 합주"), rehearsal(2L, TEAM_ID, "2차 합주")));
        given(teamMemberService.getNames(List.of(TEAM_ID))).willReturn(Map.of(TEAM_ID, "무명밴드"));

        RehearsalsResponse response = rehearsalService.getTeamRehearsals(MEMBER_ID, TEAM_ID, FROM, TO);

        verify(teamMemberService).ensureActiveMember(TEAM_ID, MEMBER_ID);
        assertThat(response.rehearsals()).extracting("title").containsExactly("1차 합주", "2차 합주");
        assertThat(response.rehearsals()).extracting("teamName").containsOnly("무명밴드");
    }

    @Test
    @DisplayName("팀 합주 목록: 팀원이 아니면 NO_PERMISSION이고 조회하지 않는다")
    void getTeamRehearsals_rejects_nonMember() {
        givenNotMember(TEAM_ID, MEMBER_ID);

        assertErrorCode(() -> rehearsalService.getTeamRehearsals(MEMBER_ID, TEAM_ID, FROM, TO),
                ErrorCode.NO_PERMISSION);
        verify(rehearsalRepository, never()).findInPeriod(any(), any(), any());
    }

    @Test
    @DisplayName("팀 합주 목록: 없는·해체된 팀은 TEAM_NOT_FOUND가 그대로 올라온다")
    void getTeamRehearsals_rejects_unknownOrDisbandedTeam() {
        willThrow(new BusinessException(ErrorCode.TEAM_NOT_FOUND))
                .given(teamMemberService).ensureActiveMember(TEAM_ID, MEMBER_ID);

        assertErrorCode(() -> rehearsalService.getTeamRehearsals(MEMBER_ID, TEAM_ID, FROM, TO),
                ErrorCode.TEAM_NOT_FOUND);
    }

    @Test
    @DisplayName("기간: 둘 다 생략하면 이번 달 1일부터 다음 달 1일 0시 전까지다")
    void getTeamRehearsals_defaultsToThisMonth() {
        YearMonth thisMonth = YearMonth.now();
        LocalDateTime start = thisMonth.atDay(1).atStartOfDay();
        LocalDateTime end = thisMonth.plusMonths(1).atDay(1).atStartOfDay();
        given(rehearsalRepository.findInPeriod(TEAM_ID, start, end)).willReturn(List.of());
        given(teamMemberService.getNames(List.of(TEAM_ID))).willReturn(Map.of(TEAM_ID, "무명밴드"));

        RehearsalsResponse response = rehearsalService.getTeamRehearsals(MEMBER_ID, TEAM_ID, null, null);

        assertThat(response.rehearsals()).isEmpty();
        verify(rehearsalRepository).findInPeriod(TEAM_ID, start, end);
    }

    @Test
    @DisplayName("기간: 하나만 보냈거나 to가 from보다 앞서거나 3달을 넘으면 VALIDATION_FAILED이고 팀 확인도 하지 않는다")
    void getTeamRehearsals_rejects_invalidPeriod() {
        assertErrorCode(() -> rehearsalService.getTeamRehearsals(MEMBER_ID, TEAM_ID, FROM, null),
                ErrorCode.VALIDATION_FAILED);
        assertErrorCode(() -> rehearsalService.getTeamRehearsals(MEMBER_ID, TEAM_ID, null, TO),
                ErrorCode.VALIDATION_FAILED);
        assertErrorCode(() -> rehearsalService.getTeamRehearsals(MEMBER_ID, TEAM_ID, TO, FROM),
                ErrorCode.VALIDATION_FAILED);
        assertErrorCode(() -> rehearsalService.getTeamRehearsals(MEMBER_ID, TEAM_ID, FROM, LocalDate.of(2026, 12, 2)),
                ErrorCode.VALIDATION_FAILED);
        verify(teamMemberService, never()).ensureActiveMember(any(), any());
    }

    @Test
    @DisplayName("기간: from의 석 달 뒤 같은 날까지는 허용하고 하루 짜리 기간도 허용한다")
    void getTeamRehearsals_allowsThreeMonthsAndSingleDay() {
        given(rehearsalRepository.findInPeriod(any(), any(), any())).willReturn(List.of());
        given(teamMemberService.getNames(List.of(TEAM_ID))).willReturn(Map.of(TEAM_ID, "무명밴드"));

        assertThatCode(() -> rehearsalService.getTeamRehearsals(MEMBER_ID, TEAM_ID, FROM, LocalDate.of(2026, 12, 1)))
                .doesNotThrowAnyException();
        assertThatCode(() -> rehearsalService.getTeamRehearsals(MEMBER_ID, TEAM_ID, FROM, FROM))
                .doesNotThrowAnyException();
    }

    // ---------- 내 합주 일정 ----------

    @Test
    @DisplayName("내 합주 일정: 활동 중인 팀이 없으면 조회하지 않고 빈 목록이다")
    void getMyRehearsals_withoutTeams_returnsEmpty() {
        given(teamService.findMemberships(MEMBER_ID)).willReturn(List.of());

        RehearsalsResponse response = rehearsalService.getMyRehearsals(MEMBER_ID, FROM, TO);

        assertThat(response.rehearsals()).isEmpty();
        verify(rehearsalRepository, never()).findInPeriodByTeamIds(any(), any(), any());
    }

    @Test
    @DisplayName("내 합주 일정: 활동 중인 여러 팀의 합주에 각 팀의 이름을 붙여 돌려준다")
    void getMyRehearsals_attachesEachTeamName() {
        given(teamService.findMemberships(MEMBER_ID))
                .willReturn(List.of(membership(5L, "무명밴드"), membership(6L, "두번째팀")));
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 1, 0, 0);
        given(rehearsalRepository.findInPeriodByTeamIds(Set.of(5L, 6L), start, end))
                .willReturn(List.of(rehearsal(1L, 6L, "두번째팀 합주"), rehearsal(2L, 5L, "무명밴드 합주")));

        RehearsalsResponse response = rehearsalService.getMyRehearsals(MEMBER_ID, FROM, TO);

        assertThat(response.rehearsals()).extracting("title", "teamName")
                .containsExactly(
                        tuple("두번째팀 합주", "두번째팀"),
                        tuple("무명밴드 합주", "무명밴드"));
    }

    @Test
    @DisplayName("내 합주 일정: 기간이 잘못되면 VALIDATION_FAILED이고 팀 조회도 하지 않는다")
    void getMyRehearsals_rejects_invalidPeriod() {
        assertErrorCode(() -> rehearsalService.getMyRehearsals(MEMBER_ID, TO, FROM), ErrorCode.VALIDATION_FAILED);
        verify(teamService, never()).findMemberships(any());
    }

    // ---------- 합주 상세 ----------

    @Test
    @DisplayName("합주 상세: 없는 합주는 REHEARSAL_NOT_FOUND다")
    void getDetail_rejects_unknownRehearsal() {
        given(rehearsalRepository.findById(REHEARSAL_ID)).willReturn(Optional.empty());

        assertErrorCode(() -> rehearsalService.getDetail(MEMBER_ID, REHEARSAL_ID), ErrorCode.REHEARSAL_NOT_FOUND);
    }

    @Test
    @DisplayName("합주 상세: 그 팀의 팀원이 아니면 NO_PERMISSION이고 작성자 정보를 조회하지 않는다")
    void getDetail_rejects_nonMember() {
        given(rehearsalRepository.findById(REHEARSAL_ID)).willReturn(Optional.of(rehearsal(REHEARSAL_ID, TEAM_ID, "합주")));
        givenNotMember(TEAM_ID, MEMBER_ID);

        assertErrorCode(() -> rehearsalService.getDetail(MEMBER_ID, REHEARSAL_ID), ErrorCode.NO_PERMISSION);
        verify(userService, never()).getSummaries(any());
    }

    @Test
    @DisplayName("합주 상세: 팀 이름·작성자 요약을 채우고, 작성자나 팀장이면 canEdit이 true다")
    void getDetail_fillsTeamAndCreator_canEditForCreatorAndLeader() {
        given(rehearsalRepository.findById(REHEARSAL_ID)).willReturn(Optional.of(rehearsal(REHEARSAL_ID, TEAM_ID, "합주")));
        given(teamMemberService.getNames(List.of(TEAM_ID))).willReturn(Map.of(TEAM_ID, "무명밴드"));
        UserSummaryResponse creator = new UserSummaryResponse(CREATOR_ID, "작성자", null);
        given(userService.getSummaries(List.of(CREATOR_ID))).willReturn(Map.of(CREATOR_ID, creator));
        given(teamMemberService.isLeader(TEAM_ID, LEADER_ID)).willReturn(true);

        RehearsalDetailResponse byCreator = rehearsalService.getDetail(CREATOR_ID, REHEARSAL_ID);
        RehearsalDetailResponse byLeader = rehearsalService.getDetail(LEADER_ID, REHEARSAL_ID);

        assertThat(byCreator.team().name()).isEqualTo("무명밴드");
        assertThat(byCreator.createdBy()).isEqualTo(creator);
        assertThat(byCreator.canEdit()).isTrue();
        assertThat(byLeader.canEdit()).isTrue();
    }

    @Test
    @DisplayName("합주 상세: 작성자도 팀장도 아닌 팀원은 canEdit이 false다")
    void getDetail_canEditFalse_forOtherMember() {
        given(rehearsalRepository.findById(REHEARSAL_ID)).willReturn(Optional.of(rehearsal(REHEARSAL_ID, TEAM_ID, "합주")));
        given(teamMemberService.getNames(List.of(TEAM_ID))).willReturn(Map.of(TEAM_ID, "무명밴드"));
        given(userService.getSummaries(List.of(CREATOR_ID))).willReturn(Map.of());
        given(teamMemberService.isLeader(TEAM_ID, MEMBER_ID)).willReturn(false);

        RehearsalDetailResponse response = rehearsalService.getDetail(MEMBER_ID, REHEARSAL_ID);

        assertThat(response.canEdit()).isFalse();
        assertThat(response.createdBy()).isNull();
    }
}
