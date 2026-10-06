package com.moyeorock.domain.recruit.service;

import com.moyeorock.domain.recruit.dto.request.RecruitPostCreateRequest;
import com.moyeorock.domain.recruit.dto.request.RecruitPostSearchCondition;
import com.moyeorock.domain.recruit.dto.request.RecruitPostStatusUpdateRequest;
import com.moyeorock.domain.recruit.dto.request.RecruitPostUpdateRequest;
import com.moyeorock.domain.recruit.dto.request.WantedSlotRequest;
import com.moyeorock.domain.recruit.dto.response.RecruitPostDetailResponse;
import com.moyeorock.domain.recruit.dto.response.RecruitPostStatusResponse;
import com.moyeorock.domain.recruit.dto.response.RecruitPostSummaryResponse;
import com.moyeorock.domain.recruit.entity.RecruitPost;
import com.moyeorock.domain.recruit.entity.WantedSlot;
import com.moyeorock.domain.recruit.enums.RecruitStatus;
import com.moyeorock.domain.recruit.repository.RecruitPostRepository;
import com.moyeorock.global.common.dto.PageResponse;
import com.moyeorock.global.common.enums.TargetType;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 권한은 임시 정책이다 — Notion 명세의 Actor는 "모임장 / 팀장"(작성·수정·마감)인데, 팀장·모임장을 확인할
 * 2팀 TeamService·4팀 GroupService가 아직 머지되지 않아 지금은 "수정·마감은 작성자 본인만, 작성은 로그인만"이다.
 * 대상 팀/모임의 존재·소속 검증도 같은 이유로 없다. 두 Service가 생기면 create/update/close의 권한 검증을 교체한다.
 */
@Service
@RequiredArgsConstructor
public class RecruitPostService {

    // 명세의 공고 목록에는 정렬 파라미터가 없다 — 최신순 고정. 클라이언트 sort를 그대로 받으면
    // 엔티티에 없는 속성이 들어왔을 때 500이 난다.
    private static final Sort LATEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final RecruitPostRepository recruitPostRepository;

    @Transactional
    public RecruitPostDetailResponse create(Long userId, RecruitPostCreateRequest request) {
        // API 계약(targetType+targetId)을 exclusive-arc 컬럼(targetTeamId/targetGroupId)으로 변환한다.
        Long targetTeamId = request.targetType() == TargetType.TEAM ? request.targetId() : null;
        Long targetGroupId = request.targetType() == TargetType.GROUP ? request.targetId() : null;
        RecruitPost post = recruitPostRepository.save(RecruitPost.create(
                userId, targetTeamId, targetGroupId, request.title(), request.body(),
                toWantedSlots(request.wantedSlots()), request.region()));
        return RecruitPostDetailResponse.of(post, userId);
    }

    /** 단일 쿼리라 트랜잭션을 열지 않는다. */
    public PageResponse<RecruitPostSummaryResponse> search(RecruitPostSearchCondition condition, Pageable pageable) {
        Pageable latestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), LATEST_FIRST);
        return PageResponse.from(recruitPostRepository
                .search(condition.targetType(), condition.region(), condition.instrument(), condition.status(),
                        latestFirst)
                .map(RecruitPostSummaryResponse::from));
    }

    /** 단일 쿼리라 트랜잭션을 열지 않는다. */
    public RecruitPostDetailResponse getDetail(Long userId, Long postId) {
        return RecruitPostDetailResponse.of(getPostOrThrow(postId), userId);
    }

    @Transactional
    public RecruitPostDetailResponse update(Long userId, Long postId, RecruitPostUpdateRequest request) {
        RecruitPost post = getPostOrThrow(postId);
        ensureAuthor(post, userId);
        post.update(request.title(), request.body(), toWantedSlots(request.wantedSlots()), request.region());
        // updatedAt(@LastModifiedDate)은 flush 때 갱신된다. 응답이 커밋보다 먼저 만들어지므로 여기서
        // flush하지 않으면 수정 전 updatedAt이 그대로 내려간다(Notion 예시는 수정 후 값).
        recruitPostRepository.flush();
        return RecruitPostDetailResponse.of(post, userId);
    }

    @Transactional
    public RecruitPostStatusResponse close(Long userId, Long postId, RecruitPostStatusUpdateRequest request) {
        RecruitPost post = getPostOrThrow(postId);
        ensureAuthor(post, userId);
        // 지원하는 전이는 OPEN -> CLOSED 하나다(명세: 변경할 상태 `CLOSED`). 재오픈은 없다.
        if (request.status() != RecruitStatus.CLOSED || post.getStatus() == RecruitStatus.CLOSED) {
            throw new BusinessException(ErrorCode.INVALID_STATE);
        }
        post.close();
        return RecruitPostStatusResponse.from(post);
    }

    private RecruitPost getPostOrThrow(Long postId) {
        return recruitPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECRUIT_POST_NOT_FOUND));
    }

    private void ensureAuthor(RecruitPost post, Long userId) {
        if (!post.isAuthor(userId)) {
            throw new BusinessException(ErrorCode.NOT_POST_AUTHOR);
        }
    }

    private List<WantedSlot> toWantedSlots(List<WantedSlotRequest> requests) {
        return requests.stream().map(r -> new WantedSlot(r.instrument(), r.count())).toList();
    }
}
