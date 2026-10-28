package com.moyeorock.domain.join.entity;

import com.moyeorock.domain.join.enums.JoinDirection;
import com.moyeorock.domain.join.enums.JoinStatus;
import com.moyeorock.global.common.entity.BaseTimeEntity;
import com.moyeorock.global.common.enums.Instrument;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 가입 신청(JoinRequestController)과 초대(InvitationController)를 한 엔티티·테이블로 처리한다
// (domains.md "헷갈리는 경계" — direction으로만 구분, 별도 Invitation 엔티티를 만들지 않는다).
//
// target_team_id·target_group_id는 exclusive-arc(recruit_posts와 동일 패턴) — create()에서
// 정확히 하나만 값을 갖는지 검증한다.
//
// user_id/inviter_id는 2026-09-16에 actor_id/target_user_id에서 개명됐다(erd.md §11) —
// user_id는 direction과 무관하게 항상 "가입 대상자"(APPLY=신청자, INVITE=초대받은 사람)이고,
// inviter_id는 INVITE일 때만 값을 갖는 "초대한 사람"이다.
@Entity
@Table(name = "join_requests",
        indexes = {
                @Index(name = "idx_join_requests_target_team_id_status", columnList = "target_team_id, status"),
                @Index(name = "idx_join_requests_target_group_id_status", columnList = "target_group_id, status"),
                @Index(name = "idx_join_requests_user_id_status", columnList = "user_id, status")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JoinRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private JoinDirection direction;

    @Column(name = "target_team_id")
    private Long targetTeamId;

    @Column(name = "target_group_id")
    private Long targetGroupId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "inviter_id")
    private Long inviterId;

    @Column(name = "recruit_post_id")
    private Long recruitPostId;

    // 지원 세션 — GROUP 대상 신청은 NULL(erd.md §11)
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Instrument instrument;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private JoinStatus status;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    public static JoinRequest apply(Long userId, Long targetTeamId, Long targetGroupId, Long recruitPostId,
            Instrument instrument, String message) {
        return create(JoinDirection.APPLY, targetTeamId, targetGroupId, userId, null, recruitPostId,
                instrument, message);
    }

    public static JoinRequest invite(Long inviterId, Long inviteeUserId, Long targetTeamId, Long targetGroupId,
            Instrument instrument, String message) {
        return create(JoinDirection.INVITE, targetTeamId, targetGroupId, inviteeUserId, inviterId, null,
                instrument, message);
    }

    private static JoinRequest create(JoinDirection direction, Long targetTeamId, Long targetGroupId, Long userId,
            Long inviterId, Long recruitPostId, Instrument instrument, String message) {
        if ((targetTeamId == null) == (targetGroupId == null)) {
            // recruit_posts와 같은 이유(RecruitPost.create() 참고) — 여기 도달하면 Service의
            // targetType->컬럼 매핑이 잘못된 것이라 사용자 입력 문제가 아니다.
            throw new IllegalArgumentException("targetTeamId, targetGroupId 중 정확히 하나만 있어야 합니다.");
        }
        JoinRequest request = new JoinRequest();
        request.direction = direction;
        request.targetTeamId = targetTeamId;
        request.targetGroupId = targetGroupId;
        request.userId = userId;
        request.inviterId = inviterId;
        request.recruitPostId = recruitPostId;
        request.instrument = instrument;
        request.message = message;
        request.status = JoinStatus.PENDING;
        return request;
    }

    public boolean isPending() {
        return status == JoinStatus.PENDING;
    }

    public void approve(Long decidedBy) {
        this.status = JoinStatus.APPROVED;
        this.decidedBy = decidedBy;
        this.decidedAt = LocalDateTime.now();
    }

    public void reject(Long decidedBy) {
        this.status = JoinStatus.REJECTED;
        this.decidedBy = decidedBy;
        this.decidedAt = LocalDateTime.now();
    }

    public void cancel(Long decidedBy) {
        this.status = JoinStatus.CANCELED;
        this.decidedBy = decidedBy;
        this.decidedAt = LocalDateTime.now();
    }
}
