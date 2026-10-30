package com.moyeorock.domain.group.dto.response;

import com.moyeorock.domain.group.entity.Group;

/**
 * 다른 도메인이 화면에 모임을 표시할 때 쓰는 최소 정보 — 공연 상세의 {@code group} 등.
 * 요청자의 역할은 담지 않는다. 역할까지 필요하면 {@link GroupSummaryResponse}다.
 */
public record GroupBriefResponse(Long id, String name, String coverImage) {

    public static GroupBriefResponse from(Group group) {
        return new GroupBriefResponse(group.getId(), group.getName(), group.getCoverImage());
    }
}
