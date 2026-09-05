package com.moyeorock.domain.team.entity;

import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.domain.team.enums.MemberStatus;
import com.moyeorock.domain.team.enums.TeamRole;
// import com.moyeorock.domain.user.entity.User;
// → 1팀의 User 엔티티가 아직 없어서 주석 처리. Performance와 동일한 방식으로,
//   User가 생기면 이 import와 아래 필드·생성자 파라미터 주석만 풀면 되게 맞춰뒀다.
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
// import jakarta.persistence.UniqueConstraint;
// → 유니크 제약 (team_id, user_id)도 user 필드와 함께 주석 처리(아래 @Table 설명 참고).
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
// @Table에 uniqueConstraints = (team_id, user_id)를 원래는 넣어야 하지만(ERD "유니크
// (team_id, user_id)"), user 필드를 아직 매핑 안 해서 user_id 컬럼 자체가 존재하지 않는다.
// application.yml이 ddl-auto: update라 없는 컬럼을 제약 대상으로 지정하면 스키마 생성이 실패한다.
// 그래서 User 엔티티가 생겨서 user 필드를 되살릴 때 이 유니크 제약도 같이 추가한다.
@Table(name = "team_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    // --- user 연관관계는 User 엔티티가 없어서 임시로 주석 처리 ---
    // 원래 형태: @ManyToOne(fetch = LAZY) @JoinColumn(name = "user_id", nullable = false) User user;
    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "user_id", nullable = false)
    // private User user;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private TeamRole role;

    // instrument는 ERD상 nullable 여부가 따로 명시되지 않았고(그냥 VARCHAR(20)), 담당 세션을
    // 아직 안 정한 팀원도 있을 수 있다고 보고 nullable 기본값(true)을 그대로 둔다. -> PR 단계에서 리뷰어랑 상의후 결정하고 ERD에 반영
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Instrument instrument;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private MemberStatus status;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    // 정적 팩토리. user 파라미터는 User 엔티티가 생기면 (Team team, User user, TeamRole role,
    // Instrument instrument) 형태로 추가한다. 지금은 team만 필수로 받는다.
    public static TeamMember create(Team team, TeamRole role, Instrument instrument) {
        TeamMember member = new TeamMember();
        member.team = team;
        // member.user = user; // User 엔티티 생기면 채운다.
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
