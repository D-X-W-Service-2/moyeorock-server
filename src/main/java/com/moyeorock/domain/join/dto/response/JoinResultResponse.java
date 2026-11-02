package com.moyeorock.domain.join.dto.response;

/**
 * 상태 변경 한 엔드포인트가 결과에 따라 다른 모양으로 응답하므로(승인·수락은 {@link JoinApprovedResponse},
 * 거절·취소는 {@link JoinDecisionResponse}) 반환 타입을 하나로 묶는 표식이다. JSON은 각 record의 모양 그대로 나간다.
 */
public sealed interface JoinResultResponse permits JoinApprovedResponse, JoinDecisionResponse {
}
