package com.moyeorock.domain.recruit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moyeorock.domain.recruit.dto.request.RecruitPostCreateRequest;
import com.moyeorock.domain.recruit.dto.request.RecruitPostSearchCondition;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class RecruitPostServiceTest {

    private static final Long AUTHOR_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Mock
    private RecruitPostRepository recruitPostRepository;

    @InjectMocks
    private RecruitPostService recruitPostService;

    private RecruitPostCreateRequest createRequest(TargetType targetType, Long targetId) {
        return new RecruitPostCreateRequest(targetType, targetId, "베이스 1명 구합니다", "주 1회 홍대에서 합주합니다.",
                List.of(new WantedSlotRequest(Instrument.BASS, 1)), Region.SEOUL);
    }

    private RecruitPost existingPost(RecruitStatus status) {
        RecruitPost post = RecruitPost.create(AUTHOR_ID, 5L, null, "제목", "본문",
                List.of(new WantedSlot(Instrument.BASS, 1)), Region.SEOUL);
        if (status == RecruitStatus.CLOSED) {
            post.close();
        }
        return post;
    }

    private void stubFind(RecruitPost post) {
        when(recruitPostRepository.findById(10L)).thenReturn(Optional.of(post));
    }

    private static BusinessException thrownBy(Runnable action) {
        return (BusinessException) org.assertj.core.api.Assertions.catchThrowable(action::run);
    }

    @Test
    @DisplayName("TEAM 대상 공고는 targetTeamId로만 저장되고 응답에서 targetType·target.id로 복원된다")
    void create_withTeamTarget() {
        when(recruitPostRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RecruitPostDetailResponse response = recruitPostService.create(AUTHOR_ID, createRequest(TargetType.TEAM, 5L));

        ArgumentCaptor<RecruitPost> saved = ArgumentCaptor.forClass(RecruitPost.class);
        verify(recruitPostRepository).save(saved.capture());
        assertThat(saved.getValue().getTargetTeamId()).isEqualTo(5L);
        assertThat(saved.getValue().getTargetGroupId()).isNull();
        assertThat(response.targetType()).isEqualTo(TargetType.TEAM);
        assertThat(response.target().id()).isEqualTo(5L);
        assertThat(response.status()).isEqualTo(RecruitStatus.OPEN);
        assertThat(response.canEdit()).isTrue();
    }

    @Test
    @DisplayName("GROUP 대상 공고는 targetGroupId로만 저장된다")
    void create_withGroupTarget() {
        when(recruitPostRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        recruitPostService.create(AUTHOR_ID, createRequest(TargetType.GROUP, 9L));

        ArgumentCaptor<RecruitPost> saved = ArgumentCaptor.forClass(RecruitPost.class);
        verify(recruitPostRepository).save(saved.capture());
        assertThat(saved.getValue().getTargetTeamId()).isNull();
        assertThat(saved.getValue().getTargetGroupId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("목록은 클라이언트가 보낸 sort를 무시하고 최신순으로 고정한다")
    void search_ignoresClientSort_andOrdersLatestFirst() {
        when(recruitPostRepository.search(any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());
        Pageable clientPageable = PageRequest.of(2, 10, Sort.by("nonexistentProperty"));

        recruitPostService.search(new RecruitPostSearchCondition(TargetType.TEAM, Region.SEOUL, Instrument.BASS,
                RecruitStatus.OPEN), clientPageable);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(recruitPostRepository).search(eq(TargetType.TEAM), eq(Region.SEOUL), eq(Instrument.BASS),
                eq(RecruitStatus.OPEN), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    @Test
    @DisplayName("목록 항목은 요약 응답으로 변환된다")
    void search_mapsToSummary() {
        when(recruitPostRepository.search(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(existingPost(RecruitStatus.OPEN))));

        var response = recruitPostService.search(new RecruitPostSearchCondition(null, null, null, null),
                PageRequest.of(0, 20));

        assertThat(response.content()).singleElement().satisfies(item -> {
            assertThat(item.title()).isEqualTo("제목");
            assertThat(item.wantedSlots()).singleElement().satisfies(slot -> {
                assertThat(slot.instrument()).isEqualTo(Instrument.BASS);
                assertThat(slot.count()).isEqualTo(1);
            });
        });
    }

    @Test
    @DisplayName("존재하지 않는 공고를 조회하면 RECRUIT_POST_NOT_FOUND가 발생한다")
    void getDetail_fails_whenPostNotFound() {
        when(recruitPostRepository.findById(999L)).thenReturn(Optional.empty());

        assertThat(thrownBy(() -> recruitPostService.getDetail(AUTHOR_ID, 999L)).getErrorCode())
                .isEqualTo(ErrorCode.RECRUIT_POST_NOT_FOUND);
    }

    @Test
    @DisplayName("작성자가 조회하면 canEdit=true, 다른 사용자가 조회하면 false다")
    void getDetail_canEdit_dependsOnViewer() {
        stubFind(existingPost(RecruitStatus.OPEN));

        assertThat(recruitPostService.getDetail(AUTHOR_ID, 10L).canEdit()).isTrue();
        assertThat(recruitPostService.getDetail(OTHER_USER_ID, 10L).canEdit()).isFalse();
    }

    @Test
    @DisplayName("작성자가 아니면 공고를 수정할 수 없다")
    void update_fails_whenCallerIsNotAuthor() {
        stubFind(existingPost(RecruitStatus.OPEN));
        RecruitPostUpdateRequest request = new RecruitPostUpdateRequest("수정", "수정 본문",
                List.of(new WantedSlotRequest(Instrument.DRUM, 1)), Region.SEOUL);

        assertThat(thrownBy(() -> recruitPostService.update(OTHER_USER_ID, 10L, request)).getErrorCode())
                .isEqualTo(ErrorCode.NOT_POST_AUTHOR);
    }

    @Test
    @DisplayName("작성자가 수정하면 제목·본문·모집 슬롯·지역이 바뀌고 대상(target)은 그대로다")
    void update_changesFields_keepsTarget() {
        RecruitPost post = existingPost(RecruitStatus.OPEN);
        stubFind(post);

        RecruitPostDetailResponse response = recruitPostService.update(AUTHOR_ID, 10L,
                new RecruitPostUpdateRequest("수정된 제목", "수정된 본문",
                        List.of(new WantedSlotRequest(Instrument.DRUM, 2)), Region.BUSAN));

        assertThat(response.title()).isEqualTo("수정된 제목");
        assertThat(response.region()).isEqualTo(Region.BUSAN);
        assertThat(response.wantedSlots()).singleElement()
                .satisfies(slot -> assertThat(slot.instrument()).isEqualTo(Instrument.DRUM));
        assertThat(response.targetType()).isEqualTo(TargetType.TEAM);
        assertThat(response.target().id()).isEqualTo(5L);
    }

    @Test
    @DisplayName("작성 시 모집 슬롯에 같은 직군이 두 번 있으면 VALIDATION_FAILED이고 저장하지 않는다")
    void create_fails_whenWantedSlotsHaveDuplicateInstrument() {
        RecruitPostCreateRequest request = new RecruitPostCreateRequest(TargetType.TEAM, 5L, "제목", "본문",
                List.of(new WantedSlotRequest(Instrument.BASS, 1), new WantedSlotRequest(Instrument.BASS, 2)),
                Region.SEOUL);

        assertThat(thrownBy(() -> recruitPostService.create(AUTHOR_ID, request)).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        verify(recruitPostRepository, never()).save(any());
    }

    @Test
    @DisplayName("수정 시 모집 슬롯에 같은 직군이 두 번 있으면 VALIDATION_FAILED이고 기존 슬롯은 그대로다")
    void update_fails_whenWantedSlotsHaveDuplicateInstrument() {
        RecruitPost post = existingPost(RecruitStatus.OPEN);
        stubFind(post);
        RecruitPostUpdateRequest request = new RecruitPostUpdateRequest("수정", "수정 본문",
                List.of(new WantedSlotRequest(Instrument.DRUM, 1), new WantedSlotRequest(Instrument.DRUM, 1)),
                Region.SEOUL);

        assertThat(thrownBy(() -> recruitPostService.update(AUTHOR_ID, 10L, request)).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(post.getWantedSlots()).singleElement()
                .satisfies(slot -> assertThat(slot.instrument()).isEqualTo(Instrument.BASS));
    }

    @Test
    @DisplayName("작성자가 아니면 공고를 마감할 수 없다")
    void close_fails_whenCallerIsNotAuthor() {
        stubFind(existingPost(RecruitStatus.OPEN));

        assertThat(thrownBy(() -> recruitPostService.close(OTHER_USER_ID, 10L,
                new RecruitPostStatusUpdateRequest(RecruitStatus.CLOSED))).getErrorCode())
                .isEqualTo(ErrorCode.NOT_POST_AUTHOR);
    }

    @Test
    @DisplayName("이미 마감된 공고를 다시 마감하면 INVALID_STATE가 발생한다")
    void close_fails_whenAlreadyClosed() {
        stubFind(existingPost(RecruitStatus.CLOSED));

        assertThat(thrownBy(() -> recruitPostService.close(AUTHOR_ID, 10L,
                new RecruitPostStatusUpdateRequest(RecruitStatus.CLOSED))).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    @DisplayName("OPEN으로 되돌리는 요청(재오픈)은 지원하지 않아 INVALID_STATE가 발생한다")
    void close_fails_whenRequestedStatusIsOpen() {
        stubFind(existingPost(RecruitStatus.OPEN));

        assertThat(thrownBy(() -> recruitPostService.close(AUTHOR_ID, 10L,
                new RecruitPostStatusUpdateRequest(RecruitStatus.OPEN))).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    @DisplayName("작성자가 마감하면 상태가 CLOSED로 바뀐다")
    void close_succeeds_whenCallerIsAuthor() {
        stubFind(existingPost(RecruitStatus.OPEN));

        RecruitPostStatusResponse response = recruitPostService.close(AUTHOR_ID, 10L,
                new RecruitPostStatusUpdateRequest(RecruitStatus.CLOSED));

        assertThat(response.status()).isEqualTo(RecruitStatus.CLOSED);
    }
}
