package com.moyeorock.domain.recruit.entity;

import com.moyeorock.domain.recruit.enums.RecruitStatus;
import com.moyeorock.global.common.entity.BaseEntity;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.common.enums.TargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// target_team_id·target_group_id는 exclusive-arc(erd.md 2026-09-16 전환) — 다형성 target_type+
// target_id를 대상 타입별 컬럼으로 나눈 것. 정확히 하나만 값을 가져야 하고, erd.md 기준으로
// FK·CHECK 제약은 DB에 걸지 않는다("검증은 애플리케이션에서") — 그래서 이 불변식은 create()의
// Java 검증이 유일한 강제 수단이다. 참조 컬럼은 전부 @ManyToOne 없이 Long으로 매핑한다.
@Entity
@Table(name = "recruit_posts",
        indexes = {
                @Index(name = "idx_recruit_posts_status_region_created_at",
                        columnList = "status, region, created_at"),
                @Index(name = "idx_recruit_posts_target_team_id", columnList = "target_team_id"),
                @Index(name = "idx_recruit_posts_target_group_id", columnList = "target_group_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecruitPost extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_team_id")
    private Long targetTeamId;

    @Column(name = "target_group_id")
    private Long targetGroupId;

    // 작성자. User 엔티티가 아직 없지만, 이 도메인 안에서는 id 비교(본인 검증)만 하면 되므로
    // @ManyToOne 없이 Long만으로 충분하다.
    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String body;

    @Convert(converter = WantedSlotsConverter.class)
    @Column(name = "wanted_slots", columnDefinition = "json", nullable = false)
    private List<WantedSlot> wantedSlots;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private Region region;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private RecruitStatus status;

    public static RecruitPost create(Long authorId, Long targetTeamId, Long targetGroupId,
            String title, String body, List<WantedSlot> wantedSlots, Region region) {
        if ((targetTeamId == null) == (targetGroupId == null)) {
            // 여기 도달하면 Service의 targetType->컬럼 매핑이 잘못된 것이다(CHK_RP_TARGET이
            // 지키는 불변식과 동일). 사용자 입력 문제가 아니라 내부 버그라 BusinessException이
            // 아니라 방어적으로 막는다.
            throw new IllegalArgumentException("targetTeamId, targetGroupId 중 정확히 하나만 있어야 합니다.");
        }
        RecruitPost post = new RecruitPost();
        post.authorId = authorId;
        post.targetTeamId = targetTeamId;
        post.targetGroupId = targetGroupId;
        post.title = title;
        post.body = body;
        post.wantedSlots = wantedSlots;
        post.region = region;
        post.status = RecruitStatus.OPEN;
        return post;
    }

    public boolean isAuthor(Long userId) {
        return this.authorId.equals(userId);
    }

    // API 계약(dto-naming.md §5)은 여전히 targetType+targetId 하나로 노출한다 — DB만
    // exclusive-arc로 나뉘었을 뿐 프론트가 보는 모양은 안 바뀐다. 두 컬럼 중 채워진 쪽에서
    // 역산한다.
    public TargetType getTargetType() {
        return targetTeamId != null ? TargetType.TEAM : TargetType.GROUP;
    }

    public Long getTargetId() {
        return targetTeamId != null ? targetTeamId : targetGroupId;
    }

    // targetType·targetId는 여기 없다 — dto-naming.md §5 "대상 변경은 허용하지 않는다".
    public void update(String title, String body, List<WantedSlot> wantedSlots, Region region) {
        this.title = title;
        this.body = body;
        this.wantedSlots = wantedSlots;
        this.region = region;
    }

    public void close() {
        this.status = RecruitStatus.CLOSED;
    }
}
