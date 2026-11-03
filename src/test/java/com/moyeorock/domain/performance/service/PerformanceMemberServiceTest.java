package com.moyeorock.domain.performance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.moyeorock.domain.group.service.GroupMemberService;
import com.moyeorock.domain.performance.dto.response.PerformanceBriefResponse;
import com.moyeorock.domain.performance.entity.Performance;
import com.moyeorock.domain.performance.repository.PerformanceRepository;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PerformanceMemberServiceTest {

    private static final Long GROUP_ID = 1L;
    private static final Long USER_ID = 20L;
    private static final Long PERFORMANCE_ID = 100L;
    private static final LocalDateTime PERFORMED_AT = LocalDateTime.of(2026, 11, 22, 18, 0);

    @Mock
    private PerformanceRepository performanceRepository;

    @Mock
    private GroupMemberService groupMemberService;

    @InjectMocks
    private PerformanceMemberService performanceMemberService;

    @Test
    @DisplayName("공연을 연 모임의 활동 모임원이면 통과한다")
    void ensureGroupMember_passes_whenActiveMember() {
        givenPerformance();
        when(groupMemberService.isActiveMember(GROUP_ID, USER_ID)).thenReturn(true);

        assertThatCode(() -> performanceMemberService.ensureGroupMember(PERFORMANCE_ID, USER_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("공연을 연 모임의 모임원이 아니면(해체된 모임 포함) NOT_PERFORMANCE_GROUP_MEMBER다")
    void ensureGroupMember_throws_whenNotGroupMember() {
        givenPerformance();
        when(groupMemberService.isActiveMember(GROUP_ID, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> performanceMemberService.ensureGroupMember(PERFORMANCE_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_PERFORMANCE_GROUP_MEMBER);
    }

    @Test
    @DisplayName("없는 공연이면 PERFORMANCE_NOT_FOUND이고 모임원 확인은 하지 않는다")
    void ensureGroupMember_throws_whenPerformanceMissing() {
        when(performanceRepository.findById(PERFORMANCE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> performanceMemberService.ensureGroupMember(PERFORMANCE_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PERFORMANCE_NOT_FOUND);
        verifyNoInteractions(groupMemberService);
    }

    @Test
    @DisplayName("공연 요약에는 공연 id·제목·일시와 모임 이름이 담긴다")
    void getBrief_returnsPerformanceWithGroupName() {
        givenPerformance();
        when(groupMemberService.getNames(List.of(GROUP_ID))).thenReturn(Map.of(GROUP_ID, "홍대 밴드부"));

        PerformanceBriefResponse brief = performanceMemberService.getBrief(PERFORMANCE_ID);

        assertThat(brief).isEqualTo(new PerformanceBriefResponse(PERFORMANCE_ID, "2026 정기공연", "홍대 밴드부",
                PERFORMED_AT));
    }

    @Test
    @DisplayName("모임이 해체됐으면 공연 요약의 모임 이름은 null이다")
    void getBrief_returnsNullGroupName_whenGroupDisbanded() {
        givenPerformance();
        when(groupMemberService.getNames(List.of(GROUP_ID))).thenReturn(Map.of());

        assertThat(performanceMemberService.getBrief(PERFORMANCE_ID).groupName()).isNull();
    }

    @Test
    @DisplayName("없는 공연의 요약을 물으면 PERFORMANCE_NOT_FOUND다")
    void getBrief_throws_whenPerformanceMissing() {
        when(performanceRepository.findById(PERFORMANCE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> performanceMemberService.getBrief(PERFORMANCE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PERFORMANCE_NOT_FOUND);
    }

    private void givenPerformance() {
        Performance performance = Performance.create(GROUP_ID, 10L, "2026 정기공연", "설명", PERFORMED_AT,
                "홍대 롤링홀", null);
        ReflectionTestUtils.setField(performance, "id", PERFORMANCE_ID);
        when(performanceRepository.findById(PERFORMANCE_ID)).thenReturn(Optional.of(performance));
    }
}
