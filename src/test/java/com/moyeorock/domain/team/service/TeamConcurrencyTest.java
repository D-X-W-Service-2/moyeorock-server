package com.moyeorock.domain.team.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.team.dto.request.TeamMemberStatusUpdateRequest;
import com.moyeorock.domain.team.dto.request.TeamMemberUpdateRequest;
import com.moyeorock.domain.team.dto.request.TeamStatusUpdateRequest;
import com.moyeorock.domain.team.dto.request.TeamUpdateRequest;
import com.moyeorock.domain.team.entity.Team;
import com.moyeorock.domain.team.entity.TeamMember;
import com.moyeorock.domain.team.enums.MemberStatus;
import com.moyeorock.domain.team.enums.TeamRole;
import com.moyeorock.domain.team.enums.TeamStatus;
import com.moyeorock.domain.team.repository.TeamGenreRepository;
import com.moyeorock.domain.team.repository.TeamMemberRepository;
import com.moyeorock.domain.team.repository.TeamRepository;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 같은 팀에 요청이 동시에 올 때 팀 행 잠금이 줄을 세우는지 실제 MySQL에서 확인한다.
 *
 * <p>요청마다 따로 커밋되어야 서로 엇갈릴 수 있으므로 클래스에 {@code @Transactional}을 붙이지 않는다.
 * 대신 테스트가 끝나면 남은 행을 직접 지운다(다른 테스트 데이터를 오염시키지 않도록).
 * 한 번으로는 우연히 통과할 수 있어서 같은 상황을 여러 번 반복한다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class TeamConcurrencyTest {

    private static final int ROUNDS = 20;
    private static final long LEADER_ID = 1L;
    private static final long B_ID = 2L;
    private static final long C_ID = 3L;
    private static final long PERFORMANCE_ID = 7L;

    @Autowired
    private TeamService teamService;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private TeamMemberRepository teamMemberRepository;
    @Autowired
    private TeamGenreRepository teamGenreRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        teamMemberRepository.deleteAllInBatch();
        teamGenreRepository.deleteAllInBatch();
        teamRepository.deleteAllInBatch();
    }

    private Team saveTeam(Team newTeam) {
        Team team = teamRepository.save(newTeam);
        teamMemberRepository.save(TeamMember.create(team.getId(), LEADER_ID, TeamRole.LEADER, Instrument.BASS));
        teamMemberRepository.save(TeamMember.create(team.getId(), B_ID, TeamRole.MEMBER, Instrument.DRUM));
        teamMemberRepository.save(TeamMember.create(team.getId(), C_ID, TeamRole.MEMBER, Instrument.KEY));
        return team;
    }

    private Team saveTeam() {
        return saveTeam(Team.create("무명밴드", null, Region.SEOUL));
    }

    /** 모든 작업이 한 지점에서 출발하도록 맞춰 동시에 실행하고, 작업마다 성공이면 null, 실패면 던진 예외를 돌려준다. */
    private List<Throwable> runAtTheSameTime(Runnable... tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.length);
        try {
            CyclicBarrier start = new CyclicBarrier(tasks.length);
            List<Future<Throwable>> futures = new ArrayList<>();
            for (Runnable task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        task.run();
                        return null;
                    } catch (Throwable t) {
                        return t;
                    }
                }));
            }
            List<Throwable> results = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private long activeLeaderCount(Long teamId) {
        return teamMemberRepository.findActiveMembers(teamId).stream()
                .filter(m -> m.getRole() == TeamRole.LEADER)
                .count();
    }

    private static TeamMemberUpdateRequest delegateToLeader() {
        return new TeamMemberUpdateRequest(TeamRole.LEADER, null);
    }

    @Test
    @DisplayName("팀장이 두 사람에게 동시에 위임해도 한 요청만 성공하고 팀장은 한 명만 남는다")
    void concurrentDelegation_keepsASingleLeader() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Long teamId = saveTeam().getId();

            List<Throwable> results = runAtTheSameTime(
                    () -> teamService.changeMember(LEADER_ID, teamId, B_ID, delegateToLeader()),
                    () -> teamService.changeMember(LEADER_ID, teamId, C_ID, delegateToLeader()));

            // 먼저 처리된 위임이 성공하면 요청자는 더 이상 팀장이 아니라서 나중 요청은 팀장 아님으로 거절된다
            assertThat(results).filteredOn(Objects::isNull).hasSize(1);
            assertThat(results).filteredOn(Objects::nonNull).singleElement()
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_TEAM_LEADER));
            assertThat(activeLeaderCount(teamId)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("정보 수정과 해체가 동시에 와도 해체가 수정에 덮여 사라지지 않는다")
    void updateAndDisbandAtTheSameTime_keepsTeamDisbanded() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Long teamId = saveTeam().getId();

            runAtTheSameTime(
                    () -> teamService.update(LEADER_ID, teamId, new TeamUpdateRequest("유명밴드", null, null, null)),
                    () -> teamService.disband(LEADER_ID, teamId, new TeamStatusUpdateRequest(TeamStatus.DISBANDED)));

            assertThat(teamRepository.findById(teamId).orElseThrow().getStatus()).isEqualTo(TeamStatus.DISBANDED);
        }
    }

    @Test
    @DisplayName("정보 수정과 공연 종료 일괄 해체가 동시에 와도 해체가 사라지지 않는다")
    void updateAndDisbandAllByPerformanceAtTheSameTime_keepsTeamDisbanded() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Long teamId = saveTeam(Team.createForPerformance(PERFORMANCE_ID, "공연팀", null, Region.SEOUL)).getId();

            runAtTheSameTime(
                    () -> teamService.update(LEADER_ID, teamId, new TeamUpdateRequest("유명밴드", null, null, null)),
                    () -> teamService.disbandAllByPerformance(PERFORMANCE_ID));

            assertThat(teamRepository.findById(teamId).orElseThrow().getStatus()).isEqualTo(TeamStatus.DISBANDED);
            cleanUp();
        }
    }

    /**
     * 위의 동시 실행 테스트는 타이밍이 맞아야 문제가 드러난다. 여기서는 타이밍에 기대지 않고 잠금 자체를 확인한다:
     * 한 트랜잭션이 팀 행을 잠그고 있는 동안, 팀의 상태나 팀원 구성을 바꾸는 요청이 모두 기다려야 한다.
     * 잠금을 쓰지 않는 요청이 있으면 그 요청은 바로 끝나서 이 테스트가 실패한다.
     */
    @Test
    @DisplayName("팀 행이 잠겨 있는 동안 쓰기 요청은 모두 끝나지 않고 기다린다")
    void writeRequests_waitWhileTheTeamRowIsLocked() throws Exception {
        Long teamId = saveTeam().getId();
        Long performanceTeamId = saveTeam(Team.createForPerformance(PERFORMANCE_ID, "공연팀", null, Region.SEOUL)).getId();

        assertWaitsForLock(teamId, () -> teamService.update(LEADER_ID, teamId,
                new TeamUpdateRequest("유명밴드", null, null, null)));
        assertWaitsForLock(teamId, () -> teamService.changeMember(LEADER_ID, teamId, B_ID,
                new TeamMemberUpdateRequest(null, Instrument.EL_GT)));
        assertWaitsForLock(teamId, () -> teamService.changeMemberStatus(B_ID, teamId, B_ID,
                new TeamMemberStatusUpdateRequest(MemberStatus.LEFT)));
        assertWaitsForLock(teamId, () -> teamService.addMember(teamId, 99L, Instrument.VOCAL));
        assertWaitsForLock(performanceTeamId, () -> teamService.disbandAllByPerformance(PERFORMANCE_ID));
        // 해체는 팀 상태를 바꾸므로 같은 팀의 다른 확인이 끝난 뒤 마지막에 한다
        assertWaitsForLock(teamId, () -> teamService.disband(LEADER_ID, teamId,
                new TeamStatusUpdateRequest(TeamStatus.DISBANDED)));
    }

    // lockedTeamId 팀을 잠근 채로 write를 실행시켜 반 초 동안 끝나지 않는지 보고, 잠금을 놓으면 끝나는지 확인한다
    private void assertWaitsForLock(Long lockedTeamId, Runnable write) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> waiting = new TransactionTemplate(transactionManager).execute(status -> {
                teamRepository.findByIdForUpdate(lockedTeamId);
                Future<?> future = pool.submit(() -> {
                    try {
                        write.run();
                    } catch (BusinessException ignored) {
                        // 잠금이 풀린 뒤의 결과(성공/실패)는 이 테스트의 관심사가 아니다
                    }
                });
                assertThatThrownBy(() -> future.get(500, TimeUnit.MILLISECONDS))
                        .as("팀 행이 잠겨 있는데도 요청이 기다리지 않고 끝났다")
                        .isInstanceOf(TimeoutException.class);
                return future;
            });
            waiting.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }
}
