package com.moyeorock.domain.recruit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.moyeorock.domain.recruit.dto.request.RecruitPostCreateRequest;
import com.moyeorock.domain.recruit.dto.request.RecruitPostStatusUpdateRequest;
import com.moyeorock.domain.recruit.dto.request.RecruitPostUpdateRequest;
import com.moyeorock.domain.recruit.dto.request.WantedSlotRequest;
import com.moyeorock.domain.recruit.dto.response.RecruitPostDetailResponse;
import com.moyeorock.domain.recruit.dto.response.RecruitPostStatusResponse;
import com.moyeorock.domain.recruit.entity.RecruitPost;
import com.moyeorock.domain.recruit.entity.WantedSlot;
import com.moyeorock.domain.recruit.enums.RecruitStatus;
import com.moyeorock.domain.recruit.repository.RecruitPostRepository;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.common.enums.TargetType;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecruitPostServiceTest {

    private static final Long AUTHOR_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Mock
    private RecruitPostRepository recruitPostRepository;

    @InjectMocks
    private RecruitPostService recruitPostService;

    private RecruitPostCreateRequest createRequest() {
        return new RecruitPostCreateRequest(
                TargetType.TEAM, 5L, "베이스 1명 구합니다", "주 1회 홍대에서 합주합니다.",
                List.of(new WantedSlotRequest(Instrument.BASS, 1)), Region.SEOUL);
    }

    private RecruitPost existingPost(Long authorId, RecruitStatus status) {
        RecruitPost post = RecruitPost.create(authorId, 5L, null, "제목", "본문",
                List.of(new WantedSlot(Instrument.BASS, 1)), Region.SEOUL);
        if (status == RecruitStatus.CLOSED) {
            post.close();
        }
        return post;
    }

    @Test
    @DisplayName("작성자가 공고를 생성하면 canEdit이 true인 상세 응답을 돌려받는다")
    void create_returnsDetailResponse_withCanEditTrue() {
        when(recruitPostRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RecruitPostDetailResponse response = recruitPostService.create(AUTHOR_ID, createRequest());

        assertThat(response.title()).isEqualTo("베이스 1명 구합니다");
        assertThat(response.status()).isEqualTo(RecruitStatus.OPEN);
        assertThat(response.canEdit()).isTrue();
        // targetType=TEAM 요청이 targetTeamId로만 저장되고(exclusive-arc), 응답에서
        // targetType·target.id로 정확히 역산되는지 확인한다.
        assertThat(response.targetType()).isEqualTo(TargetType.TEAM);
        assertThat(response.target().id()).isEqualTo(5L);
    }

    @Test
    @DisplayName("targetType=GROUP 요청이면 targetGroupId로만 저장된다")
    void create_withGroupTarget_resolvesToGroupId() {
        when(recruitPostRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        RecruitPostCreateRequest groupRequest = new RecruitPostCreateRequest(
                TargetType.GROUP, 9L, "제목", "본문",
                List.of(new WantedSlotRequest(Instrument.BASS, 1)), Region.SEOUL);

        RecruitPostDetailResponse response = recruitPostService.create(AUTHOR_ID, groupRequest);

        assertThat(response.targetType()).isEqualTo(TargetType.GROUP);
        assertThat(response.target().id()).isEqualTo(9L);
    }

    @Test
    @DisplayName("존재하지 않는 공고를 조회하면 RECRUIT_POST_NOT_FOUND가 발생한다")
    void getDetail_fails_whenPostNotFound() {
        when(recruitPostRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recruitPostService.getDetail(AUTHOR_ID, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RECRUIT_POST_NOT_FOUND);
    }

    @Test
    @DisplayName("작성자가 아니면 공고를 수정할 수 없다")
    void update_fails_whenCallerIsNotAuthor() {
        RecruitPost post = existingPost(AUTHOR_ID, RecruitStatus.OPEN);
        when(recruitPostRepository.findById(10L)).thenReturn(Optional.of(post));
        RecruitPostUpdateRequest request = new RecruitPostUpdateRequest(
                "수정된 제목", "수정된 본문", List.of(new WantedSlotRequest(Instrument.DRUM, 1)), Region.SEOUL);

        assertThatThrownBy(() -> recruitPostService.update(OTHER_USER_ID, 10L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_POST_AUTHOR);
    }

    @Test
    @DisplayName("작성자가 아니면 공고를 마감할 수 없다")
    void close_fails_whenCallerIsNotAuthor() {
        RecruitPost post = existingPost(AUTHOR_ID, RecruitStatus.OPEN);
        when(recruitPostRepository.findById(10L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> recruitPostService.close(
                OTHER_USER_ID, 10L, new RecruitPostStatusUpdateRequest(RecruitStatus.CLOSED)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_POST_AUTHOR);
    }

    @Test
    @DisplayName("이미 마감된 공고를 다시 마감하면 INVALID_STATE가 발생한다")
    void close_fails_whenAlreadyClosed() {
        RecruitPost post = existingPost(AUTHOR_ID, RecruitStatus.CLOSED);
        when(recruitPostRepository.findById(10L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> recruitPostService.close(
                AUTHOR_ID, 10L, new RecruitPostStatusUpdateRequest(RecruitStatus.CLOSED)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    @DisplayName("작성자가 마감하면 상태가 CLOSED로 바뀐다")
    void close_succeeds_whenCallerIsAuthor() {
        RecruitPost post = existingPost(AUTHOR_ID, RecruitStatus.OPEN);
        when(recruitPostRepository.findById(10L)).thenReturn(Optional.of(post));

        RecruitPostStatusResponse response = recruitPostService.close(
                AUTHOR_ID, 10L, new RecruitPostStatusUpdateRequest(RecruitStatus.CLOSED));

        assertThat(response.status()).isEqualTo(RecruitStatus.CLOSED);
    }
}
