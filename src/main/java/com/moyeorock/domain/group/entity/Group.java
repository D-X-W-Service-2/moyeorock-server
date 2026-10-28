package com.moyeorock.domain.group.entity;

import com.moyeorock.domain.group.enums.GroupType;
import com.moyeorock.global.common.entity.BaseEntity;
import com.moyeorock.global.common.enums.Region;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 테이블명이 groups가 아니라 groups_다 — GROUPS는 MySQL 8.0.2+ 예약어(erd.md §4).
@Entity
@Table(name = "groups_")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Group extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private GroupType type;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private Region region;

    @Column(name = "cover_image", length = 500)
    private String coverImage;

    public static Group create(String name, String description, GroupType type, Region region, String coverImage) {
        Group group = new Group();
        group.name = name;
        group.description = description;
        group.type = type;
        group.region = region;
        group.coverImage = coverImage;
        return group;
    }

    public void update(String name, String description, Region region, String coverImage) {
        this.name = name;
        this.description = description;
        this.region = region;
        this.coverImage = coverImage;
    }
}
