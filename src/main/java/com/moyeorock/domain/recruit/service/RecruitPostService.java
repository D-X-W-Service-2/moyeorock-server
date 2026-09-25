package com.moyeorock.domain.recruit.service;

import com.moyeorock.domain.recruit.dto.request.RecruitPostCreateRequest;
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
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.common.enums.TargetType;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecruitPostService {

    private final RecruitPostRepository recruitPostRepository;

    @Transactional
    public RecruitPostDetailResponse create(Long userId, RecruitPostCreateRequest request) {
        // API 계약(targetType+targetId)을 exclusive-arc 컬럼(targetTeamId/targetGroupId)으로
        // 변환한다 — erd.md 2026-09-16 전환 이후에도 요청 DTO 모양은 그대로다.
        Long targetTeamId = request.targetType() == TargetType.TEAM ? request.targetId() : null;
        Long targetGroupId = request.targetType() == TargetType.GROUP ? request.targetId() : null;
        RecruitPost post = RecruitPost.create(
                userId,
                targetTeamId,
                targetGroupId,
                request.title(),
                request.body(),
                toWantedSlots(request.wantedSlots()),
                request.region());
        RecruitPost saved = recruitPostRepository.save(post);
        return RecruitPostDetailResponse.of(saved, userId);
    }

    public PageResponse<RecruitPostSummaryResponse> search(TargetType targetType, Region region,
            RecruitStatus status, Long authorId, Pageable pageable) {
        Page<RecruitPostSummaryResponse> page = recruitPostRepository
                .search(targetType, region, status, authorId, pageable)
                .map(RecruitPostSummaryResponse::from);
        return PageResponse.from(page);
    }

    public RecruitPostDetailResponse getDetail(Long userId, Long postId) {
        RecruitPost post = findByIdOrThrow(postId);
        return RecruitPostDetailResponse.of(post, userId);
    }

    @Transactional
    public RecruitPostDetailResponse update(Long userId, Long postId, RecruitPostUpdateRequest request) {
        RecruitPost post = findByIdOrThrow(postId);
        validateAuthor(post, userId);
        post.update(request.title(), request.body(), toWantedSlots(request.wantedSlots()), request.region());
        // updatedAt은 flush 시점(@LastModifiedDate)에 갱신된다. 응답을 커밋 전에 만들기 때문에
        // 여기서 flush하지 않으면 수정 전 updatedAt이 그대로 내려간다(노션 명세 예시는 수정 후 값).
        recruitPostRepository.flush();
        return RecruitPostDetailResponse.of(post, userId);
    }

    @Transactional
    public RecruitPostStatusResponse close(Long userId, Long postId, RecruitPostStatusUpdateRequest request) {
        RecruitPost post = findByIdOrThrow(postId);
        validateAuthor(post, userId);
        if (request.status() != RecruitStatus.CLOSED) {
            throw new BusinessException(ErrorCode.INVALID_STATE);
        }
        if (post.getStatus() == RecruitStatus.CLOSED) {
            throw new BusinessException(ErrorCode.INVALID_STATE);
        }
        post.close();
        return new RecruitPostStatusResponse(post.getId(), post.getStatus());
    }

    private RecruitPost findByIdOrThrow(Long postId) {
        return recruitPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECRUIT_POST_NOT_FOUND));
    }

    private void validateAuthor(RecruitPost post, Long userId) {
        if (!post.isAuthor(userId)) {
            throw new BusinessException(ErrorCode.NOT_POST_AUTHOR);
        }
    }

    private List<WantedSlot> toWantedSlots(List<WantedSlotRequest> requests) {
        return requests.stream().map(r -> new WantedSlot(r.instrument(), r.count())).toList();
    }
}
