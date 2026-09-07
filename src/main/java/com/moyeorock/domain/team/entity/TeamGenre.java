package com.moyeorock.domain.team.entity;

import com.moyeorock.global.common.enums.Genre;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private Genre genre;

    // 파라미터로 Team을 받게 해서 "소속 팀 없는 TeamGenre"가 생성 단계에서부터 불가능하게 한다.
    public static TeamGenre create(Team team, Genre genre) {
        TeamGenre teamGenre = new TeamGenre();
        teamGenre.team = team;
        teamGenre.genre = genre;
        return teamGenre;
    }
}
