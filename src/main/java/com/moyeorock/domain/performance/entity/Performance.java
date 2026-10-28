package com.moyeorock.domain.performance.entity;

import com.moyeorock.domain.performance.enums.PerformanceStatus;
import com.moyeorock.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "performances")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Performance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "performed_at")
    private LocalDateTime performedAt;

    // 7.1.1: 필수 아님
    @Column(length = 100)
    private String venue;

    @Column(name = "poster_image", length = 500)
    private String posterImage;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PerformanceStatus status;

    @Column(name = "created_by")
    private Long createdBy;

    public static Performance create(Long groupId, Long createdBy, String title, String description,
            LocalDateTime performedAt, String venue, String posterImage) {
        Performance performance = new Performance();
        performance.groupId = groupId;
        performance.createdBy = createdBy;
        performance.title = title;
        performance.description = description;
        performance.performedAt = performedAt;
        performance.venue = venue;
        performance.posterImage = posterImage;
        performance.status = PerformanceStatus.PLANNED;
        return performance;
    }

    public void update(String title, String description, LocalDateTime performedAt, String venue,
            String posterImage) {
        this.title = title;
        this.description = description;
        this.performedAt = performedAt;
        this.venue = venue;
        this.posterImage = posterImage;
    }

    public void changeStatus(PerformanceStatus status) {
        this.status = status;
    }
}
