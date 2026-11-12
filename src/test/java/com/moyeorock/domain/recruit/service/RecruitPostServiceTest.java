package com.moyeorock.domain.recruit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moyeorock.domain.group.dto.response.GroupBriefResponse;
import com.moyeorock.domain.group.service.GroupMemberService;
import com.moyeorock.domain.join.service.JoinRequestStatService;
import com.moyeorock.domain.recruit.dto.response.RecruitPostTargetResponse;
import com.moyeorock.domain.team.dto.response.TeamSummaryResponse;
import com.moyeorock.domain.team.enums.TeamStatus;
import com.moyeorock.domain.team.service.TeamMemberService;
import com.moyeorock.domain.user.dto.response.UserSummaryResponse;
import com.moyeorock.domain.user.service.UserService;
import com.moyeorock.global.common.enums.Genre;
import java.util.Collection;
import java.util.Map;
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

    @Mock
    private UserService userService;

    @Mock
    private TeamMemberService teamMemberService;

    @Mock
    private GroupMemberService groupMemberService;

    @Mock
    private JoinRequestStatService joinRequestStatService;

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

    private RecruitPost existingGroupPost() {
        return RecruitPost.create(AUTHOR_ID, null, 9L, "모임 공고", "본문",
                List.of(new WantedSlot(Instrument.BASS, 1)), Region.SEOUL);
    }

    private static TeamSummaryResponse teamSummary(Long id) {
        return new TeamSummaryResponse(id, "무명밴드", Region.SEOUL, List.of(Genre.ROCK), TeamStatus.ACTIVE, 3);
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
        verify(teamMemberService).ensureLeader(5L, AUTHOR_ID);
    }

    @Test
    @DisplayName("팀 공고는 팀 요약(이름·지역·장르)과 작성자 요약, 현재 팀장 기준 canEdit이 채워진다")
    void create_fillsTargetAuthorAndCanEdit() {
        when(recruitPostRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(teamMemberService.getSummaries(List.of(5L))).thenReturn(Map.of(5L, teamSummary(5L)));
        when(userService.getSummaries(List.of(AUTHOR_ID)))
                .thenReturn(Map.of(AUTHOR_ID, new UserSummaryResponse(AUTHOR_ID, "민서", null)));
        when(teamMemberService.isLeader(5L, AUTHOR_ID)).thenReturn(true);

        RecruitPostDetailResponse response = recruitPostService.create(AUTHOR_ID, createRequest(TargetType.TEAM, 5L));

        assertThat(response.target()).isEqualTo(new RecruitPostTargetResponse(5L, "무명밴드", Region.SEOUL, List.of(Genre.ROCK)));
        assertThat(response.author().nickname()).isEqualTo("민서");
        assertThat(response.canEdit()).isTrue();
    }

    @Test
    @DisplayName("팀장이 아니거나 없는 팀이면 공고를 만들 수 없고 저장하지 않는다")
    void create_fails_whenNotLeaderOfTeam() {
        doThrow(new BusinessException(ErrorCode.NOT_TEAM_LEADER)).when(teamMemberService).ensureLeader(5L, OTHER_USER_ID);
        doThrow(new BusinessException(ErrorCode.TEAM_NOT_FOUND)).when(teamMemberService).ensureLeader(6L, AUTHOR_ID);

        assertThat(thrownBy(() -> recruitPostService.create(OTHER_USER_ID, createRequest(TargetType.TEAM, 5L)))
                .getErrorCode()).isEqualTo(ErrorCode.NOT_TEAM_LEADER);
        assertThat(thrownBy(() -> recruitPostService.create(AUTHOR_ID, createRequest(TargetType.TEAM, 6L)))
                .getErrorCode()).isEqualTo(ErrorCode.TEAM_NOT_FOUND);
        verify(recruitPostRepository, never()).save(any());
    }

    @Test
    @DisplayName("공연 팀은 팀장이어도 공고를 만들 수 없고 저장하지 않는다")
    void create_fails_whenTargetIsPerformanceTeam() {
        when(teamMemberService.isPerformanceTeam(5L)).thenReturn(true);

        assertThat(thrownBy(() -> recruitPostService.create(AUTHOR_ID, createRequest(TargetType.TEAM, 5L)))
                .getErrorCode()).isEqualTo(ErrorCode.PERFORMANCE_TEAM_RECRUIT_NOT_ALLOWED);
        verify(teamMemberService).ensureLeader(5L, AUTHOR_ID);
        verify(recruitPostRepository, never()).save(any());
    }

    @Test
    @DisplayName("팀장 확인이 공연 팀 확인보다 먼저라 팀장이 아니면 403이다")
    void create_checksLeaderBeforePerformanceTeam() {
        doThrow(new BusinessException(ErrorCode.NOT_TEAM_LEADER)).when(teamMemberService).ensureLeader(5L, OTHER_USER_ID);

        assertThat(thrownBy(() -> recruitPostService.create(OTHER_USER_ID, createRequest(TargetType.TEAM, 5L)))
                .getErrorCode()).isEqualTo(ErrorCode.NOT_TEAM_LEADER);
        verify(teamMemberService, never()).isPerformanceTeam(any());
    }

    @Test
    @DisplayName("GROUP 대상 공고는 targetGroupId로만 저장된다")
    void create_withGroupTarget() {
        when(recruitPostRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(groupMemberService.getGroupBrief(9L)).thenReturn(new GroupBriefResponse(9L, "홍대동아리", null));

        recruitPostService.create(AUTHOR_ID, createRequest(TargetType.GROUP, 9L));

        ArgumentCaptor<RecruitPost> saved = ArgumentCaptor.forClass(RecruitPost.class);
        verify(recruitPostRepository).save(saved.capture());
        assertThat(saved.getValue().getTargetTeamId()).isNull();
        assertThat(saved.getValue().getTargetGroupId()).isEqualTo(9L);
        verify(groupMemberService).ensureOwner(9L, AUTHOR_ID);
        verify(teamMemberService, never()).ensureLeader(any(), any());
        verify(teamMemberService, never()).isPerformanceTeam(any());
    }

    @Test
    @DisplayName("모임장이 아니거나 없는 모임이면 모임 공고를 만들 수 없다")
    void create_fails_whenNotOwnerOfGroup() {
        doThrow(new BusinessException(ErrorCode.NOT_GROUP_OWNER)).when(groupMemberService).ensureOwner(9L, OTHER_USER_ID);

        assertThat(thrownBy(() -> recruitPostService.create(OTHER_USER_ID, createRequest(TargetType.GROUP, 9L)))
                .getErrorCode()).isEqualTo(ErrorCode.NOT_GROUP_OWNER);
        verify(recruitPostRepository, never()).save(any());
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
    @DisplayName("목록 항목은 요약 응답으로 변환되고 팀 요약은 한 번에 일괄 조회한다")
    void search_mapsToSummary() {
        when(recruitPostRepository.search(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(existingPost(RecruitStatus.OPEN), existingPost(RecruitStatus.OPEN))));
        when(teamMemberService.getSummaries(any())).thenReturn(Map.of(5L, teamSummary(5L)));

        var response = recruitPostService.search(new RecruitPostSearchCondition(null, null, null, null),
                PageRequest.of(0, 20));

        assertThat(response.content()).hasSize(2).allSatisfy(item -> {
            assertThat(item.title()).isEqualTo("제목");
            assertThat(item.target().name()).isEqualTo("무명밴드");
            assertThat(item.wantedSlots()).singleElement().satisfies(slot -> {
                assertThat(slot.instrument()).isEqualTo(Instrument.BASS);
                assertThat(slot.count()).isEqualTo(1);
            });
        });
        ArgumentCaptor<Collection<Long>> teamIds = ArgumentCaptor.forClass(Collection.class);
        verify(teamMemberService).getSummaries(teamIds.capture());
        assertThat(teamIds.getValue()).containsExactly(5L);
    }

    @Test
    @DisplayName("목록에서 해체돼 요약이 없는 팀·모임은 공고를 빼지 않고 target에 id만 남긴다 (모임은 중복 id를 한 번만 조회)")
    void search_unresolvedTargetsKeepIdOnly() {
        when(recruitPostRepository.search(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(existingPost(RecruitStatus.OPEN), existingGroupPost(), existingGroupPost())));
        when(teamMemberService.getSummaries(any())).thenReturn(Map.of());
        when(groupMemberService.getGroupBrief(9L)).thenThrow(new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        var response = recruitPostService.search(new RecruitPostSearchCondition(null, null, null, null),
                PageRequest.of(0, 20));

        assertThat(response.content()).hasSize(3);
        assertThat(response.content().get(0).target()).isEqualTo(new RecruitPostTargetResponse(5L, null, null, null));
        assertThat(response.content().get(1).target()).isEqualTo(new RecruitPostTargetResponse(9L, null, null, null));
        verify(groupMemberService, times(1)).getGroupBrief(9L);
    }

    @Test
    @DisplayName("모임 공고의 target은 모임 이름만 채워지고 region·genres는 null이다")
    void getDetail_groupTargetHasNameOnly() {
        stubFind(existingGroupPost());
        when(groupMemberService.getGroupBrief(9L)).thenReturn(new GroupBriefResponse(9L, "홍대동아리", null));

        RecruitPostDetailResponse response = recruitPostService.getDetail(OTHER_USER_ID, 10L);

        assertThat(response.target()).isEqualTo(new RecruitPostTargetResponse(9L, "홍대동아리", null, null));
    }

    @Test
    @DisplayName("존재하지 않는 공고를 조회하면 RECRUIT_POST_NOT_FOUND가 발생한다")
    void getDetail_fails_whenPostNotFound() {
        when(recruitPostRepository.findById(999L)).thenReturn(Optional.empty());

        assertThat(thrownBy(() -> recruitPostService.getDetail(AUTHOR_ID, 999L)).getErrorCode())
                .isEqualTo(ErrorCode.RECRUIT_POST_NOT_FOUND);
    }

    @Test
    @DisplayName("현재 팀장이 조회하면 canEdit=true, 아니면 false다 (작성자라도 팀장이 아니면 false)")
    void getDetail_canEdit_dependsOnCurrentLeader() {
        stubFind(existingPost(RecruitStatus.OPEN));
        when(teamMemberService.getSummaries(List.of(5L))).thenReturn(Map.of(5L, teamSummary(5L)));
        when(teamMemberService.isLeader(5L, OTHER_USER_ID)).thenReturn(true);
        when(teamMemberService.isLeader(5L, AUTHOR_ID)).thenReturn(false);

        assertThat(recruitPostService.getDetail(OTHER_USER_ID, 10L).canEdit()).isTrue();
        assertThat(recruitPostService.getDetail(AUTHOR_ID, 10L).canEdit()).isFalse();
    }

    @Test
    @DisplayName("해체돼 요약에서 빠진 팀은 팀장 행이 남아 있어도 canEdit=false다 — isLeader는 해체 여부를 보지 않는다")
    void getDetail_canEdit_falseWhenTeamDisbanded() {
        stubFind(existingPost(RecruitStatus.OPEN));
        when(teamMemberService.getSummaries(List.of(5L))).thenReturn(Map.of());

        assertThat(recruitPostService.getDetail(AUTHOR_ID, 10L).canEdit()).isFalse();
        verify(teamMemberService, never()).isLeader(any(), any());
    }

    @Test
    @DisplayName("모임 공고의 canEdit은 현재 모임장 여부다")
    void getDetail_canEdit_groupOwner() {
        stubFind(existingGroupPost());
        when(groupMemberService.isOwner(9L, OTHER_USER_ID)).thenReturn(true);
        when(groupMemberService.getGroupBrief(9L)).thenReturn(new GroupBriefResponse(9L, "홍대동아리", null));

        assertThat(recruitPostService.getDetail(OTHER_USER_ID, 10L).canEdit()).isTrue();
        assertThat(recruitPostService.getDetail(AUTHOR_ID, 10L).canEdit()).isFalse();
    }

    @Test
    @DisplayName("탈퇴한 작성자는 요약이 없어 author가 null이다")
    void getDetail_withdrawnAuthorIsNull() {
        stubFind(existingPost(RecruitStatus.OPEN));
        when(userService.getSummaries(List.of(AUTHOR_ID))).thenReturn(Map.of());

        assertThat(recruitPostService.getDetail(OTHER_USER_ID, 10L).author()).isNull();
    }

    @Test
    @DisplayName("팀장이 아니면 공고를 수정할 수 없다 (작성자라도 마찬가지)")
    void update_fails_whenCallerIsNotLeader() {
        stubFind(existingPost(RecruitStatus.OPEN));
        doThrow(new BusinessException(ErrorCode.NOT_TEAM_LEADER)).when(teamMemberService).ensureLeader(5L, OTHER_USER_ID);
        RecruitPostUpdateRequest request = new RecruitPostUpdateRequest("수정", "수정 본문",
                List.of(new WantedSlotRequest(Instrument.DRUM, 1)), Region.SEOUL);

        assertThat(thrownBy(() -> recruitPostService.update(OTHER_USER_ID, 10L, request)).getErrorCode())
                .isEqualTo(ErrorCode.NOT_TEAM_LEADER);
    }

    @Test
    @DisplayName("모임 공고는 모임장만 수정할 수 있다")
    void update_groupPost_requiresOwner() {
        stubFind(existingGroupPost());
        doThrow(new BusinessException(ErrorCode.NOT_GROUP_OWNER)).when(groupMemberService).ensureOwner(9L, OTHER_USER_ID);
        RecruitPostUpdateRequest request = new RecruitPostUpdateRequest("수정", "수정 본문",
                List.of(new WantedSlotRequest(Instrument.DRUM, 1)), Region.SEOUL);

        assertThat(thrownBy(() -> recruitPostService.update(OTHER_USER_ID, 10L, request)).getErrorCode())
                .isEqualTo(ErrorCode.NOT_GROUP_OWNER);
    }

    @Test
    @DisplayName("해체돼 팀을 찾을 수 없으면 공고 수정도 TEAM_NOT_FOUND로 막힌다")
    void update_fails_whenTeamDisbanded() {
        stubFind(existingPost(RecruitStatus.OPEN));
        doThrow(new BusinessException(ErrorCode.TEAM_NOT_FOUND)).when(teamMemberService).ensureLeader(5L, AUTHOR_ID);
        RecruitPostUpdateRequest request = new RecruitPostUpdateRequest("수정", "수정 본문",
                List.of(new WantedSlotRequest(Instrument.DRUM, 1)), Region.SEOUL);

        assertThat(thrownBy(() -> recruitPostService.update(AUTHOR_ID, 10L, request)).getErrorCode())
                .isEqualTo(ErrorCode.TEAM_NOT_FOUND);
    }

    @Test
    @DisplayName("현재 팀장이 수정하면 제목·본문·모집 슬롯·지역이 바뀌고 대상(target)은 그대로다")
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
    @DisplayName("마감된 공고는 작성자도 수정할 수 없다 — INVALID_STATE이고 내용은 그대로다")
    void update_fails_whenPostIsClosed() {
        RecruitPost post = existingPost(RecruitStatus.CLOSED);
        stubFind(post);
        RecruitPostUpdateRequest request = new RecruitPostUpdateRequest("수정", "수정 본문",
                List.of(new WantedSlotRequest(Instrument.DRUM, 1)), Region.BUSAN);

        assertThat(thrownBy(() -> recruitPostService.update(AUTHOR_ID, 10L, request)).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE);
        assertThat(post.getTitle()).isEqualTo("제목");
        assertThat(post.getRegion()).isEqualTo(Region.SEOUL);
    }

    @Test
    @DisplayName("마감된 공고라도 팀장이 아니면 상태가 아니라 권한(NOT_TEAM_LEADER)으로 거절한다")
    void update_checksLeaderBeforeClosedState() {
        stubFind(existingPost(RecruitStatus.CLOSED));
        doThrow(new BusinessException(ErrorCode.NOT_TEAM_LEADER)).when(teamMemberService).ensureLeader(5L, OTHER_USER_ID);
        RecruitPostUpdateRequest request = new RecruitPostUpdateRequest("수정", "수정 본문",
                List.of(new WantedSlotRequest(Instrument.DRUM, 1)), Region.SEOUL);

        assertThat(thrownBy(() -> recruitPostService.update(OTHER_USER_ID, 10L, request)).getErrorCode())
                .isEqualTo(ErrorCode.NOT_TEAM_LEADER);
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
    @DisplayName("팀장이 아니면 공고를 마감할 수 없다")
    void close_fails_whenCallerIsNotLeader() {
        stubFind(existingPost(RecruitStatus.OPEN));
        doThrow(new BusinessException(ErrorCode.NOT_TEAM_LEADER)).when(teamMemberService).ensureLeader(5L, OTHER_USER_ID);

        assertThat(thrownBy(() -> recruitPostService.close(OTHER_USER_ID, 10L,
                new RecruitPostStatusUpdateRequest(RecruitStatus.CLOSED))).getErrorCode())
                .isEqualTo(ErrorCode.NOT_TEAM_LEADER);
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
    @DisplayName("현재 팀장이 마감하면 상태가 CLOSED로 바뀐다")
    void close_succeeds_whenCallerIsLeader() {
        stubFind(existingPost(RecruitStatus.OPEN));

        RecruitPostStatusResponse response = recruitPostService.close(AUTHOR_ID, 10L,
                new RecruitPostStatusUpdateRequest(RecruitStatus.CLOSED));

        assertThat(response.status()).isEqualTo(RecruitStatus.CLOSED);
    }

    @Test
    @DisplayName("모임 해체 때 그 모임의 모집 중 공고를 모두 마감한다")
    void closeOpenPostsOfGroup_closesEveryOpenPost() {
        List<RecruitPost> openPosts = List.of(existingPost(RecruitStatus.OPEN), existingPost(RecruitStatus.OPEN));
        when(recruitPostRepository.findByTargetGroupIdAndStatus(9L, RecruitStatus.OPEN)).thenReturn(openPosts);

        recruitPostService.closeOpenPostsOfGroup(9L);

        assertThat(openPosts).extracting(RecruitPost::getStatus).containsOnly(RecruitStatus.CLOSED);
    }
}
