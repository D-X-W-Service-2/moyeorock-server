package com.moyeorock.domain.performance.service;

import com.moyeorock.domain.group.service.GroupMemberService;
import com.moyeorock.domain.performance.dto.response.PerformanceBriefResponse;
import com.moyeorock.domain.performance.entity.Performance;
import com.moyeorock.domain.performance.repository.PerformanceRepository;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 다른 도메인(팀·가입)이 공연을 확인하려고 부르는 작은 조회 서비스다(#96·#105).
 *
 * <p>다른 Service는 {@link GroupMemberService}만 부르는 것이 이 클래스의 조건이다. 2팀 {@code TeamService}가
 * 이 Service를 부르는데 {@link PerformanceService}는 이미 {@code TeamService}를 부르므로, 공연 확인을
 * PerformanceService에 두면 Service끼리 서로 부르게 된다(architecture.md §3). {@code GroupMemberService}·
 * {@code TeamMemberService}와 같은 역할이다.
 */
@Service
@RequiredArgsConstructor
public class PerformanceMemberService {

    private final PerformanceRepository performanceRepository;
    private final GroupMemberService groupMemberService;

    /**
     * 공연을 연 모임의 활동 모임원인지 확인한다. 없는 공연은 {@code PERFORMANCE_NOT_FOUND}, 모임원이 아니면(해체된 모임 포함)
     * {@code NOT_PERFORMANCE_GROUP_MEMBER}다. 공연 팀에 사람을 넣기 전(팀원 추가·가입 신청·초대)에 부른다(#96).
     */
    public void ensureGroupMember(Long performanceId, Long userId) {
        Performance performance = getPerformanceOrThrow(performanceId);
        if (!groupMemberService.isActiveMember(performance.getGroupId(), userId)) {
            throw new BusinessException(ErrorCode.NOT_PERFORMANCE_GROUP_MEMBER);
        }
    }

    /** 공연 요약(팀 상세의 {@code performance}). 없는 공연은 {@code PERFORMANCE_NOT_FOUND}, 모임이 해체됐으면 모임 이름은 null이다. */
    public PerformanceBriefResponse getBrief(Long performanceId) {
        Performance performance = getPerformanceOrThrow(performanceId);
        String groupName = groupMemberService.getNames(List.of(performance.getGroupId())).get(performance.getGroupId());
        return PerformanceBriefResponse.of(performance, groupName);
    }

    private Performance getPerformanceOrThrow(Long performanceId) {
        return performanceRepository.findById(performanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND));
    }
}
