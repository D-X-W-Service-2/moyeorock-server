package com.moyeorock.domain.recruit.repository;

import com.moyeorock.domain.recruit.entity.RecruitPost;
import com.moyeorock.domain.recruit.enums.RecruitStatus;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.common.enums.TargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecruitPostRepository extends JpaRepository<RecruitPost, Long> {

    /**
     * 조건 4개(targetType·region·instrument·status)라 파생 메서드명 대신 @Query로 뺐다(conventions.md §4).
     * 전부 선택 조건이고 null이면 걸지 않는다.
     */
    default Page<RecruitPost> search(TargetType targetType, Region region, Instrument instrument,
            RecruitStatus status, Pageable pageable) {
        return searchByNames(targetType != null ? targetType.name() : null, region,
                instrument != null ? instrument.name() : null, status, pageable);
    }

    // targetType·instrument는 문자열 파라미터로 받는다 — 어떤 매핑 속성과도 직접 비교되지 않는 파라미터를
    // enum으로 받으면 Hibernate가 타입을 못 정해("Could not determine ValueMapping") 실패한다.
    // 오버로드하면 null 인자 호출이 모호해져 이름을 다르게 뒀고, 직접 호출하지 않고 search()로만 쓴다.
    //
    // - targetType은 저장된 컬럼이 아니라 target_team_id·target_group_id 중 어느 쪽이 채워졌는지로 판별한다.
    // - instrument는 wanted_slots(JSON 배열) 안에 해당 악기의 슬롯이 하나라도 있는지(MySQL JSON_CONTAINS)로 판별한다.
    @Query("""
            SELECT r FROM RecruitPost r
            WHERE (:targetTypeName IS NULL
                   OR (:targetTypeName = 'TEAM' AND r.targetTeamId IS NOT NULL)
                   OR (:targetTypeName = 'GROUP' AND r.targetGroupId IS NOT NULL))
              AND (:region IS NULL OR r.region = :region)
              AND (:instrumentName IS NULL
                   OR function('JSON_CONTAINS', r.wantedSlots, function('JSON_OBJECT', 'instrument', :instrumentName)) = 1)
              AND (:status IS NULL OR r.status = :status)
            """)
    Page<RecruitPost> searchByNames(@Param("targetTypeName") String targetTypeName,
            @Param("region") Region region,
            @Param("instrumentName") String instrumentName,
            @Param("status") RecruitStatus status,
            Pageable pageable);
}
