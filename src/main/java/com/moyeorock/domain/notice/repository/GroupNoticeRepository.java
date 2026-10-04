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

    // 동아리 홈(모임 상세)의 고정 공지 미리보기 — 상위 3건
    List<GroupNotice> findTop3ByGroupIdOrderByIsPinnedDescCreatedAtDesc(Long groupId);
}
