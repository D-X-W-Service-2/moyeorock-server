package com.moyeorock.domain.notice.repository;

import com.moyeorock.domain.notice.entity.GroupNotice;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupNoticeRepository extends JpaRepository<GroupNotice, Long> {

    // 고정 공지 우선, 그다음 최신순 (api-spec.md §7 "고정글 우선 정렬")
    Page<GroupNotice> findByGroupIdOrderByIsPinnedDescCreatedAtDesc(Long groupId, Pageable pageable);

    Page<GroupNotice> findByGroupIdAndIsPinnedOrderByCreatedAtDesc(
            Long groupId, boolean isPinned, Pageable pageable);

    // 동아리 홈(모임 상세)의 고정 공지 미리보기 — 고정된 것만 최신순 상위 3건.
    // 고정 여부로 필터하지 않으면 고정 공지가 없는 모임에서 일반 공지가 pinnedNotices로 내려간다.
    List<GroupNotice> findTop3ByGroupIdAndIsPinnedTrueOrderByCreatedAtDesc(Long groupId);
}
