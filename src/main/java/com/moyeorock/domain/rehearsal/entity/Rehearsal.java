package com.moyeorock.domain.rehearsal.entity;

import com.moyeorock.domain.team.entity.Team;
import com.moyeorock.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "rehearsals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Rehearsal extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 다른 테이블의 PK를 우리 테이블의 FK로 받아온다.
    @ManyToOne(fetch = FetchType.LAZY)
    // 해당 컬럼은 nullable 이긴 하지만, null이면 안되는거 같다.
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(length = 100)
    private String title;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    @Column(length = 100)
    private String place;

    @Column(columnDefinition = "TEXT")
    private String memo;

    // User 클래스가 아직 없어서 개발된 뒤에 연동 하도록 하겠습니다.
    //    @ManyToOne(fetch = FetchType.LAZY)
    //    @JoinColumn(name = "created_by")
    //    private User users;
    

}
