package com.moyeorock.domain.join.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.group.dto.request.GroupCreateRequest;
import com.moyeorock.domain.group.enums.GroupType;
import com.moyeorock.domain.group.repository.GroupMemberRepository;
import com.moyeorock.domain.group.repository.GroupRepository;
import com.moyeorock.domain.group.service.GroupService;
import com.moyeorock.domain.join.dto.request.InvitationCreateRequest;
import com.moyeorock.domain.join.dto.request.JoinRequestCreateRequest;
import com.moyeorock.domain.join.entity.JoinRequest;
import com.moyeorock.domain.join.repository.JoinRequestRepository;
import com.moyeorock.domain.notification.repository.NotificationRepository;
import com.moyeorock.domain.team.dto.request.TeamCreateRequest;
import com.moyeorock.domain.team.repository.TeamGenreRepository;
import com.moyeorock.domain.team.repository.TeamMemberRepository;
import com.moyeorock.domain.team.repository.TeamRepository;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.repository.UserRepository;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.JoinStatus;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.common.enums.TargetType;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 같은 사람·같은 대상의 신청·초대가 동시에 와도 대기 건이 하나만 생기는지 실제 MySQL에서 확인한다.
 * {@code join_requests}에는 유니크 제약이 없어서, 대상 팀·모임 행 잠금이 중복 확인과 저장을 한 줄로 세워야 한다.
 *
 * <p>요청마다 따로 커밋되어야 서로 엇갈릴 수 있으므로 클래스에 {@code @Transactional}을 붙이지 않고 테스트가 끝나면 직접 지운다.
 * 한 번으로는 우연히 통과할 수 있어서 같은 상황을 여러 번 반복한다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class JoinConcurrencyTest {

    private static final int ROUNDS = 20;

    private long leaderId;
    private long userId;

    @Autowired
    private JoinRequestService joinRequestService;
    @Autowired
    private TeamService teamService;
    @Autowired
    private GroupService groupService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JoinRequestRepository joinRequestRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private TeamMemberRepository teamMemberRepository;
    @Autowired
    private TeamGenreRepository teamGenreRepository;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @BeforeEach
    void setUp() {
        leaderId = saveUser("동시리더").getId();
        userId = saveUser("동시유저").getId();
    }

    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAllInBatch();
        joinRequestRepository.deleteAllInBatch();
        teamMemberRepository.deleteAllInBatch();
        teamGenreRepository.deleteAllInBatch();
        teamRepository.deleteAllInBatch();
        groupMemberRepository.deleteAllInBatch();
        groupRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    private User saveUser(String nickname) {
        return userRepository.saveAndFlush(User.signupWithEmail(
                nickname + "@example.com", "hash", nickname, LocalDateTime.now()));
    }

    private long createTeam() {
        return teamService.create(leaderId,
                new TeamCreateRequest("동시밴드", null, Region.SEOUL, List.of(Genre.ROCK), Instrument.EL_GT)).id();
    }

    private long createGroup() {
        return groupService.create(leaderId,
                new GroupCreateRequest("동시동아리", null, GroupType.REGULAR, Region.SEOUL, null)).id();
    }

    private long pendingCount(TargetType targetType, long targetId) {
        return joinRequestRepository.findAll().stream()
                .filter(r -> r.getUserId().equals(userId) && r.getStatus() == JoinStatus.PENDING)
                .filter(r -> r.getTargetType() == targetType && r.getTargetId().equals(targetId))
                .count();
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

    // 하나는 성공하고 나머지는 모두 JOIN_REQUEST_DUPLICATED여야 한다(유니크 위반 같은 500이 아니라)
    private static void assertOneSucceededOthersDuplicated(List<Throwable> results) {
        assertThat(results).filteredOn(Objects::isNull).hasSize(1);
        assertThat(results).filteredOn(Objects::nonNull).allSatisfy(t ->
                assertThat(t).isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.JOIN_REQUEST_DUPLICATED)));
    }

    @Test
    @DisplayName("같은 팀에 신청 두 번과 초대가 동시에 와도 대기 건은 하나만 생긴다")
    void concurrentApplyAndInvite_toTeam_leaveOnePending() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long teamId = createTeam();
            JoinRequestCreateRequest apply = new JoinRequestCreateRequest(TargetType.TEAM, teamId, null, Instrument.BASS, null);

            List<Throwable> results = runAtTheSameTime(
                    () -> joinRequestService.apply(userId, apply),
                    () -> joinRequestService.apply(userId, apply),
                    () -> joinRequestService.invite(leaderId,
                            new InvitationCreateRequest(TargetType.TEAM, teamId, userId, Instrument.BASS, null)));

            assertOneSucceededOthersDuplicated(results);
            assertThat(pendingCount(TargetType.TEAM, teamId)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("같은 모임에 신청 두 번과 초대가 동시에 와도 대기 건은 하나만 생긴다")
    void concurrentApplyAndInvite_toGroup_leaveOnePending() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long groupId = createGroup();
            JoinRequestCreateRequest apply = new JoinRequestCreateRequest(TargetType.GROUP, groupId, null, null, null);

            List<Throwable> results = runAtTheSameTime(
                    () -> joinRequestService.apply(userId, apply),
                    () -> joinRequestService.apply(userId, apply),
                    () -> joinRequestService.invite(leaderId,
                            new InvitationCreateRequest(TargetType.GROUP, groupId, userId, null, null)));

            assertOneSucceededOthersDuplicated(results);
            assertThat(pendingCount(TargetType.GROUP, groupId)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("대기 건 승인과 같은 사람의 새 신청이 동시에 와도 소속은 한 번만 추가되고 남는 대기 건이 없다")
    void approveAndReapplyAtTheSameTime_addsMemberOnce() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long teamId = createTeam();
            JoinRequestCreateRequest apply = new JoinRequestCreateRequest(TargetType.TEAM, teamId, null, Instrument.BASS, null);
            Long requestId = joinRequestService.apply(userId, apply).id();

            runAtTheSameTime(
                    () -> joinRequestService.updateStatus(leaderId, requestId, JoinStatus.APPROVED),
                    () -> joinRequestService.apply(userId, apply));

            // 승인이 먼저면 새 신청은 ALREADY_MEMBER, 신청이 먼저면 대기 건이 이미 있어 JOIN_REQUEST_DUPLICATED다
            List<JoinRequest> rows = joinRequestRepository.findAll().stream()
                    .filter(r -> r.getTargetType() == TargetType.TEAM && r.getTargetId().equals(teamId))
                    .toList();
            assertThat(rows).singleElement()
                    .satisfies(r -> assertThat(r.getStatus()).isEqualTo(JoinStatus.APPROVED));
        }
    }
}
