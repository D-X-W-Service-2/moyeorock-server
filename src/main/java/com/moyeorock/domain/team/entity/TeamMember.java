package com.moyeorock.domain.team.entity;

import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.domain.team.enums.MemberStatus;
import com.moyeorock.domain.team.enums.TeamRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// team_id·user_id는 CLAUDE.md 절대 규칙 6에 따라 Long으로 매핑한다 — 같은 도메인 내부 참조도
// 예외 없음(team 필드는 원래 @ManyToOne Team이었으나 규칙 6 확정 후 미반영 상태였다).
// user_id는 1팀 User 엔티티가 없어 계속 비워둔 채였는데, 이번에 User가 생겨서 채운다.
@Entity
@Table(name = "team_members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private TeamRole role;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Instrument instrument;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private MemberStatus status;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    public static TeamMember create(Long teamId, Long userId, TeamRole role, Instrument instrument) {
        TeamMember member = new TeamMember();
        member.teamId = teamId;
        member.userId = userId;
        member.role = role;
        member.instrument = instrument;
        // 새로 합류하는 팀원은 항상 ACTIVE. LEFT·REMOVED는 나중에 별도 메서드로만 바뀌는 상태라
        member.status = MemberStatus.ACTIVE;
        member.joinedAt = LocalDateTime.now();
        return member;
    }

    // 역할 위임(ERD "위임 가능"). setRole이 아니라 changeRole이라는 이름으로, 호출부에서
    // "단순 값 대입"이 아니라 "역할을 바꾸는 도메인 행위"임을 드러낸다.
    // 팀당 LEADER가 유일해야 한다는 규칙은 TeamMember 하나만 봐서는 검증할 수 없는(다른 멤버들과
    // 비교해야 하는) 조건이라, 여기서 검증하지 않고 TeamService에서 처리한다(architecture.md §2 —
    // 권한/도메인 규칙 검증은 Service의 책임).
    public void changeRole(TeamRole newRole) {
        this.role = newRole;
    }

    // 탈퇴(LEFT)·강퇴(REMOVED) 상태 변경. 두 상태를 별도 메서드로 안 쪼갠 이유는 dto-naming.md의
    // TeamMemberStatusUpdateRequest(status)가 이미 LEFT/REMOVED 중 하나를 값으로 받아 넘기는
    // 구조라, Service 쪽 분기(본인 탈퇴 vs 리더의 강퇴) 없이 엔티티는 그 값을 그대로 반영만 하면 된다.
    public void updateStatus(MemberStatus status) {
        this.status = status;
    }
}
