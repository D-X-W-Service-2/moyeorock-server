package com.moyeorock.domain.join.service;

import com.moyeorock.domain.join.dto.response.JoinRequestBriefResponse;
import com.moyeorock.domain.join.enums.JoinDirection;
import com.moyeorock.domain.join.repository.JoinRequestRepository;
import com.moyeorock.global.common.enums.Instrument;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 공고 상세가 신청 현황(세션별 대기 수·내 신청)을 채울 때 쓰는 조회 전용 Service.
 *
 * <p>{@code JoinRequestService}는 신청을 받을 때 {@code RecruitPostService}로 공고를 확인하고,
 * {@code RecruitPostService}는 상세를 만들 때 신청 현황이 필요해 서로 부르면 순환 참조가 된다(architecture.md §3).
 * 그래서 신청 현황 조회만 다른 Service를 부르지 않는 이 클래스로 떼어, 의존이 한 방향
 * ({@code JoinRequestService → RecruitPostService → JoinRequestStatService})으로만 흐르게 한다.
 * 여기에 다른 Service 호출을 넣으면 순환이 다시 생긴다.
 */
@Service
@RequiredArgsConstructor
public class JoinRequestStatService {

    private final JoinRequestRepository joinRequestRepository;

    /** 공고의 세션별 대기(PENDING) 신청 수. 신청이 없는 세션은 키가 없다. 호출하는 쪽이 getOrDefault로 0을 채운다. */
    public Map<Instrument, Long> countPendingByInstrument(Long recruitPostId) {
        Map<Instrument, Long> counts = new EnumMap<>(Instrument.class);
        for (Object[] row : joinRequestRepository.countPendingByInstrument(recruitPostId)) {
            // 공고 기반 신청은 TEAM 대상이면 instrument가 항상 있지만, GROUP 대상은 null일 수 있어 건너뛴다.
            if (row[0] != null) {
                counts.put((Instrument) row[0], (Long) row[1]);
            }
        }
        return counts;
    }

    /** 그 공고에 낸 내 신청 중 가장 최근 것(상태 무관). 비로그인이거나 신청 이력이 없으면 비어 있다. */
    public Optional<JoinRequestBriefResponse> findMyLatestApplication(Long recruitPostId, Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return joinRequestRepository
                .findFirstByRecruitPostIdAndUserIdAndDirectionOrderByIdDesc(recruitPostId, userId, JoinDirection.APPLY)
                .map(JoinRequestBriefResponse::from);
    }
}
