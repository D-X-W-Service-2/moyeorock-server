package com.moyeorock.domain.group.dto.response;

import com.moyeorock.domain.group.entity.GroupMember;
import com.moyeorock.domain.group.enums.GroupRole;
import java.time.LocalDateTime;

/**
 * 모임원 목록 항목, 그리고 역할 변경 응답.
 *
 * <p>명세(Notion API 초안)는 여기에 {@code nickname}·{@code profileImage}·{@code instruments}를
 * 더 담지만, 전부 user 도메인 소유라 {@code UserService}가 머지되기 전에는 채울 수 없다(PR #57).
 * 지금은 group이 가진 값만 내려주고, 머지 후 일괄 조회로 세 필드를 붙인다 —
 * {@code status}는 담지 않는다(목록이 ACTIVE만 조회하므로 전부 같은 값).
 */
public record GroupMemberResponse(Long userId, GroupRole role, LocalDateTime joinedAt) {

    public static GroupMemberResponse from(GroupMember member) {
        return new GroupMemberResponse(member.getUserId(), member.getRole(), member.getJoinedAt());
    }
}
