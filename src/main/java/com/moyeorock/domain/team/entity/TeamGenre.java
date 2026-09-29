package com.moyeorock.domain.team.entity;

import com.moyeorock.global.common.enums.Genre;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// team_id는 CLAUDE.md 절대 규칙 6에 따라 Long으로 매핑한다 — 같은 도메인 내부 참조도 예외 없음
// (원래 @ManyToOne Team이었으나 규칙 6 확정 후 미반영 상태였던 걸 이번에 맞춤).
@Entity
@Table(
        name = "team_genres",
        uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "genre"}),
        indexes = @Index(name = "idx_team_genres_genre", columnList = "genre"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamGenre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private Genre genre;

    public static TeamGenre create(Long teamId, Genre genre) {
        TeamGenre teamGenre = new TeamGenre();
        teamGenre.teamId = teamId;
        teamGenre.genre = genre;
        return teamGenre;
    }
}
