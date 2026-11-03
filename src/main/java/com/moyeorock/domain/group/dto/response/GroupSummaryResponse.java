package com.moyeorock.domain.group.dto.response;

import com.moyeorock.domain.group.entity.Group;
import com.moyeorock.domain.group.enums.GroupRole;
import com.moyeorock.domain.group.enums.GroupType;

/**
 * 내 모임 목록 항목. {@code role}은 그 모임에서의 <b>내 역할</b>이다.
 * {@code UserActivityResponse.groups[]} 항목과 같은 모양이다(dto-spec.md, dto-naming.md §8).
 */
public record GroupSummaryResponse(Long id, String name, GroupType type, GroupRole role, String coverImage) {

    public static GroupSummaryResponse of(Group group, GroupRole role) {
        return new GroupSummaryResponse(group.getId(), group.getName(), group.getType(), role, group.getCoverImage());
    }
}
