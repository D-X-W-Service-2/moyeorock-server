package com.moyeorock.domain.recruit.repository;

import com.moyeorock.domain.recruit.entity.RecruitPost;
import com.moyeorock.domain.recruit.enums.RecruitStatus;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.common.enums.TargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecruitPostRepository extends JpaRepository<RecruitPost, Long> {

    // 조건은 targetType·region·status(노션 API 초안 기준, authorId 없음). targetType이 컬럼이
    // 아니라 두 컬럼의 null 여부로 판별하는 파생 조건이라 파생 메서드명으로는 표현할 수 없어 @Query로 뺐다.
    //
    // targetType은 저장된 컬럼이 아니다(erd.md 2026-09-16 exclusive-arc 전환) — targetTeamId·
    // targetGroupId 중 어느 쪽이 채워져 있는지로 판별한다.
    //
    // instrument 필터(api-spec.md §4)는 여기 없다 — wanted_slots가 JSON 컬럼이라
    // MySQL의 JSON_CONTAINS 같은 네이티브 함수가 필요한데, 지금 테스트 DB(H2, MySQL 호환 모드)가
    // 이걸 지원하지 않는다. MySQL 기반 테스트 환경(Flyway·Testcontainers, PR #50)이 들어온 뒤 네이티브 쿼리로 추가한다.
    default Page<RecruitPost> search(TargetType targetType, Region region, RecruitStatus status,
            Pageable pageable) {
        return searchByTargetTypeName(targetType != null ? targetType.name() : null, region, status, pageable);
    }

    // :targetTypeName을 TargetType.TEAM 같은 완전한 enum 리터럴과 직접 비교했더니 Hibernate가
    // "Could not determine ValueMapping for SqmParameter" 오류를 냈다 — 이 파라미터가 어떤
    // 매핑된 속성과도 안 묶이고 리터럴하고만 비교돼서 타입을 못 정한 것으로 보인다. 문자열
    // 파라미터로 바꾸고 'TEAM'/'GROUP' 리터럴과 비교하니 해결됐다. 이름을 다르게 둔 이유는
    // TargetType 버전과 String 버전을 오버로드하면 둘 다 null을 받아들여서(둘 다 참조 타입)
    // 호출부에서 어느 쪽인지 모호하다는 컴파일 에러가 났기 때문 — 이 메서드는 직접 호출하지 않고
    // 위 search(TargetType, ...)를 통해서만 쓴다.
    @Query("""
            SELECT r FROM RecruitPost r
            WHERE (:targetTypeName IS NULL
                   OR (:targetTypeName = 'TEAM' AND r.targetTeamId IS NOT NULL)
                   OR (:targetTypeName = 'GROUP' AND r.targetGroupId IS NOT NULL))
              AND (:region IS NULL OR r.region = :region)
              AND (:status IS NULL OR r.status = :status)
            """)
    Page<RecruitPost> searchByTargetTypeName(@Param("targetTypeName") String targetTypeName,
            @Param("region") Region region,
            @Param("status") RecruitStatus status,
            Pageable pageable);
}
