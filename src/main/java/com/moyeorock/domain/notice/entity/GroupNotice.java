package com.moyeorock.domain.notice.entity;

import com.moyeorock.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// notice는 group과 분리된 도메인이다(domains.md "헷갈리는 경계"). 테이블은 group_notices,
// 엔티티는 GroupNotice — Post라는 이름을 쓰지 않는다.
@Entity
@Table(name = "group_notices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupNotice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String body;

    @Column(name = "is_pinned")
    private boolean isPinned;

    public static GroupNotice create(Long groupId, Long authorId, String title, String body, boolean isPinned) {
        GroupNotice notice = new GroupNotice();
        notice.groupId = groupId;
        notice.authorId = authorId;
        notice.title = title;
        notice.body = body;
        notice.isPinned = isPinned;
        return notice;
    }

    public boolean isAuthor(Long userId) {
        return this.authorId.equals(userId);
    }

    public void update(String title, String body, boolean isPinned) {
        this.title = title;
        this.body = body;
        this.isPinned = isPinned;
    }
}
