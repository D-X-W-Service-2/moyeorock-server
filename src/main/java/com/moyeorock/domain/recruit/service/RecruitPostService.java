package com.moyeorock.domain.recruit.service;

import com.moyeorock.domain.recruit.dto.request.RecruitPostCreateRequest;
import com.moyeorock.domain.recruit.dto.request.RecruitPostSearchCondition;
import com.moyeorock.domain.recruit.dto.request.RecruitPostStatusUpdateRequest;
import com.moyeorock.domain.recruit.dto.request.RecruitPostUpdateRequest;
import com.moyeorock.domain.recruit.dto.request.WantedSlotRequest;
import com.moyeorock.domain.recruit.dto.response.RecruitPostDetailResponse;
import com.moyeorock.domain.recruit.dto.response.RecruitPostStatusResponse;
import com.moyeorock.domain.recruit.dto.response.RecruitPostTargetResponse;
import com.moyeorock.domain.recruit.dto.response.RecruitPostSummaryResponse;
import com.moyeorock.domain.recruit.entity.RecruitPost;
import com.moyeorock.domain.recruit.entity.WantedSlot;
import com.moyeorock.domain.recruit.enums.RecruitStatus;
import com.moyeorock.domain.recruit.repository.RecruitPostRepository;
import com.moyeorock.domain.group.dto.response.GroupBriefResponse;
import com.moyeorock.domain.group.service.GroupMemberService;
import com.moyeorock.domain.team.dto.response.TeamSummaryResponse;
import com.moyeorock.domain.team.service.TeamMemberService;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import com.moyeorock.domain.user.service.UserService;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.TargetType;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공고의 작성·수정·마감은 <b>대상 팀의 현재 팀장 / 대상 모임의 현재 모임장</b>만 할 수 있다(Notion Actor "모임장 / 팀장").
 * 작성자가 아니라 현재 권한자 기준이라, 작성자가 팀장에서 빠지면 더는 고칠 수 없고 새 팀장이 관리한다.
 * 팀·모임이 해체되면 권한 확인이 404로 막혀 수정·마감이 안 되고, 응답의 target은 id만 남는다.
 * 모임장 확인은 {@code GroupMemberService}로 한다 — {@code GroupService}는 모임 해체 때 이 Service를
 * 부르므로({@link #closeOpenPostsOfGroup}) 여기서 {@code GroupService}를 부르면 순환 참조가 된다.
 */
@Service
@RequiredArgsConstructor
public class RecruitPostService {

    // 명세의 공고 목록에는 정렬 파라미터가 없다 — 최신순 고정. 클라이언트 sort를 그대로 받으면
    // 엔티티에 없는 속성이 들어왔을 때 500이 난다.
    private static final Sort LATEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final RecruitPostRepository recruitPostRepository;
    private final UserService userService;
    private final TeamService teamService;
    private final TeamMemberService teamMemberService;
    private final GroupMemberService groupMemberService;

    @Transactional
    public RecruitPostDetailResponse create(Long userId, RecruitPostCreateRequest request) {
        // 대상 팀/모임이 있고 요청자가 그 팀장/모임장인지 한 번에 확인한다(없으면 404, 아니면 403).
        ensureCanManage(request.targetType(), request.targetId(), userId);
        // API 계약(targetType+targetId)을 exclusive-arc 컬럼(targetTeamId/targetGroupId)으로 변환한다.
        Long targetTeamId = request.targetType() == TargetType.TEAM ? request.targetId() : null;
        Long targetGroupId = request.targetType() == TargetType.GROUP ? request.targetId() : null;
        RecruitPost post = recruitPostRepository.save(RecruitPost.create(
                userId, targetTeamId, targetGroupId, request.title(), request.body(),
                toWantedSlots(request.wantedSlots()), request.region()));
        return toDetail(post, userId);
    }

    /** 다른 도메인 서비스 호출이 섞여 있어 트랜잭션을 열지 않는다. 팀 요약은 한 번에 일괄 조회한다. */
    public PageResponse<RecruitPostSummaryResponse> search(RecruitPostSearchCondition condition, Pageable pageable) {
        Pageable latestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), LATEST_FIRST);
        Page<RecruitPost> page = recruitPostRepository.search(
                condition.targetType(), condition.region(), condition.instrument(), condition.status(), latestFirst);
        Map<Long, TeamSummaryResponse> teams = teamService.getSummaries(ids(page, RecruitPost::getTargetTeamId));
        // 모임은 일괄 조회가 없어 한 페이지(최대 20건) 안에서 중복을 뺀 id마다 단건 조회한다(4팀 일괄 메서드가 생기면 교체).
        Map<Long, GroupBriefResponse> groups = new HashMap<>();
        for (Long groupId : ids(page, RecruitPost::getTargetGroupId)) {
            findGroupBrief(groupId).ifPresent(brief -> groups.put(groupId, brief));
        }
        return PageResponse.from(page.map(post ->
                RecruitPostSummaryResponse.of(post, targetOf(post, teams, groups))));
    }

    /** 다른 도메인 서비스 호출이 섞여 있어 트랜잭션을 열지 않는다. */
    public RecruitPostDetailResponse getDetail(Long userId, Long postId) {
        return toDetail(getPostOrThrow(postId), userId);
    }

    @Transactional
    public RecruitPostDetailResponse update(Long userId, Long postId, RecruitPostUpdateRequest request) {
        RecruitPost post = getPostOrThrow(postId);
        ensureCanManage(post.getTargetType(), post.getTargetId(), userId);
        // 마감된 공고는 고칠 수 없다 — 마감 뒤에 모집 세션·인원이 바뀌면 이미 들어온 신청과 어긋난다.
        // 권한 검사를 먼저 해서 권한이 없는 사람에게는 공고 상태를 드러내지 않는다.
        if (post.getStatus() == RecruitStatus.CLOSED) {
            throw new BusinessException(ErrorCode.INVALID_STATE);
        }
        post.update(request.title(), request.body(), toWantedSlots(request.wantedSlots()), request.region());
        // updatedAt(@LastModifiedDate)은 flush 때 갱신된다. 응답이 커밋보다 먼저 만들어지므로 여기서
        // flush하지 않으면 수정 전 updatedAt이 그대로 내려간다(Notion 예시는 수정 후 값).
        recruitPostRepository.flush();
        return toDetail(post, userId);
    }

    @Transactional
    public RecruitPostStatusResponse close(Long userId, Long postId, RecruitPostStatusUpdateRequest request) {
        RecruitPost post = getPostOrThrow(postId);
        ensureCanManage(post.getTargetType(), post.getTargetId(), userId);
        // 지원하는 전이는 OPEN -> CLOSED 하나다(명세: 변경할 상태 `CLOSED`). 재오픈은 없다.
        if (request.status() != RecruitStatus.CLOSED || post.getStatus() == RecruitStatus.CLOSED) {
            throw new BusinessException(ErrorCode.INVALID_STATE);
        }
        post.close();
        return RecruitPostStatusResponse.from(post);
    }

    /**
     * 모임 해체 때 그 모임의 모집 중 공고를 마감한다({@code GroupService.disband}가 부른다). 권한 확인은 부르는 쪽이 한다.
     * 해체는 soft delete라 공고 행도 지우지 않고 마감으로 남긴다(architecture.md §6). 마감된 공고는 이미 두고 간다.
     */
    @Transactional
    public void closeOpenPostsOfGroup(Long groupId) {
        recruitPostRepository.findByTargetGroupIdAndStatus(groupId, RecruitStatus.OPEN).forEach(RecruitPost::close);
    }

    private RecruitPost getPostOrThrow(Long postId) {
        return recruitPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECRUIT_POST_NOT_FOUND));
    }

    /** 막아야 할 때: 팀이면 팀장(ensureLeader), 모임이면 모임장(ensureOwner). 없거나 해체된 대상은 404다. */
    private void ensureCanManage(TargetType targetType, Long targetId, Long userId) {
        if (targetType == TargetType.TEAM) {
            teamMemberService.ensureLeader(targetId, userId);
        } else {
            groupMemberService.ensureOwner(targetId, userId);
        }
    }

    /** 막지 않고 답만 할 때(canEdit 표시용). 해체된 대상이나 권한이 없으면 false다. */
    private boolean canManage(RecruitPost post, Long userId) {
        return post.getTargetType() == TargetType.TEAM
                ? teamMemberService.isLeader(post.getTargetId(), userId)
                : groupMemberService.isOwner(post.getTargetId(), userId);
    }

    private RecruitPostDetailResponse toDetail(RecruitPost post, Long viewerId) {
        Long authorId = post.getAuthorId();
        // 탈퇴한 작성자는 getSummaries에서 빠져 null이다
        UserSummaryResponse author = userService.getSummaries(List.of(authorId)).get(authorId);
        Map<Long, TeamSummaryResponse> teams = post.getTargetTeamId() == null ? Map.of()
                : teamService.getSummaries(List.of(post.getTargetTeamId()));
        Map<Long, GroupBriefResponse> groups = new HashMap<>();
        if (post.getTargetGroupId() != null) {
            findGroupBrief(post.getTargetGroupId()).ifPresent(brief -> groups.put(post.getTargetGroupId(), brief));
        }
        return RecruitPostDetailResponse.of(post, targetOf(post, teams, groups), author, canManage(post, viewerId));
    }

    private RecruitPostTargetResponse targetOf(RecruitPost post, Map<Long, TeamSummaryResponse> teams,
            Map<Long, GroupBriefResponse> groups) {
        if (post.getTargetType() == TargetType.TEAM) {
            TeamSummaryResponse team = teams.get(post.getTargetId());
            return team == null ? RecruitPostTargetResponse.unresolved(post) : RecruitPostTargetResponse.ofTeam(team);
        }
        GroupBriefResponse group = groups.get(post.getTargetId());
        return group == null ? RecruitPostTargetResponse.unresolved(post) : RecruitPostTargetResponse.ofGroup(group);
    }

    /** 없거나 해체된 모임은 getGroupBrief가 404를 던지므로 "없음"으로 바꾼다. 그 밖의 오류는 그대로 던진다. */
    private Optional<GroupBriefResponse> findGroupBrief(Long groupId) {
        try {
            return Optional.of(groupMemberService.getGroupBrief(groupId));
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.GROUP_NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    private static Set<Long> ids(Page<RecruitPost> page, Function<RecruitPost, Long> getter) {
        return page.getContent().stream().map(getter).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<WantedSlot> toWantedSlots(List<WantedSlotRequest> requests) {
        ensureNoDuplicateInstrument(requests);
        return requests.stream().map(r -> new WantedSlot(r.instrument(), r.count())).toList();
    }

    /** 같은 직군이 두 줄이면 집계·마감 기준이 모호해진다(예: BASS 1명 + BASS 2명). UI가 막아도 서버에서 한 번 더 막는다. */
    private void ensureNoDuplicateInstrument(List<WantedSlotRequest> requests) {
        Set<Instrument> seen = new HashSet<>();
        for (WantedSlotRequest request : requests) {
            if (!seen.add(request.instrument())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
        }
    }
}
