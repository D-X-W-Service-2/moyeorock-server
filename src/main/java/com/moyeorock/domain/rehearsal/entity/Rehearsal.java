package com.moyeorock.domain.rehearsal.entity;

import com.moyeorock.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "rehearsals",
        indexes = @Index(name = "idx_rehearsals_team_id_starts_at", columnList = "team_id, starts_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Rehearsal extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(length = 100)
    private String title;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Column(length = 100)
    private String place;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @Column(name = "created_by")
    private Long createdBy;

    public static Rehearsal create(Long teamId, Long createdBy, String title, LocalDateTime startsAt,
            LocalDateTime endsAt, String place, String memo) {
        Rehearsal rehearsal = new Rehearsal();
        rehearsal.teamId = teamId;
        rehearsal.createdBy = createdBy;
        rehearsal.title = title;
        rehearsal.startsAt = startsAt;
        rehearsal.endsAt = endsAt;
        rehearsal.place = place;
        rehearsal.memo = memo;
        return rehearsal;
    }

    public boolean isCreator(Long userId) {
        return this.createdBy.equals(userId);
    }

    public void update(String title, LocalDateTime startsAt, LocalDateTime endsAt, String place, String memo) {
        this.title = title;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.place = place;
        this.memo = memo;
    }
}
