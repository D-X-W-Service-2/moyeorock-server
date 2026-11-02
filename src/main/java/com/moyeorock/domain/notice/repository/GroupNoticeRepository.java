package com.moyeorock.domain.notice.repository;

import com.moyeorock.domain.notice.entity.GroupNotice;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

// 정렬 끝에 id를 붙여 순서를 하나로 정한다 — created_at이 같은 공지가 페이지 경계에 걸려도 중복·누락이 없다.
// 방향을 created_at과 같은 DESC로 맞춰야 (group_id, is_pinned, created_at) 인덱스(InnoDB가 끝에 PK를 붙인다)로
// 정렬이 끝난다.
public interface GroupNoticeRepository extends JpaRepository<GroupNotice, Long> {

    // 고정 공지 우선, 그다음 최신순 (api-spec.md §7 "고정글 우선 정렬")
    Page<GroupNotice> findByGroupIdOrderByIsPinnedDescCreatedAtDescIdDesc(Long groupId, Pageable pageable);

    Page<GroupNotice> findByGroupIdAndIsPinnedOrderByCreatedAtDescIdDesc(
            Long groupId, boolean isPinned, Pageable pageable);

    // 동아리 홈(모임 상세)의 고정 공지 미리보기 — 고정된 것만 최신순, 개수는 호출하는 쪽이 limit으로 정한다.
    // 고정 여부로 필터하지 않으면 고정 공지가 없는 모임에서 일반 공지가 pinnedNotices로 내려간다.
    List<GroupNotice> findByGroupIdAndIsPinnedTrueOrderByCreatedAtDescIdDesc(Long groupId, Limit limit);
}
