package com.moyeorock.domain.user.repository;

import com.moyeorock.domain.user.entity.UserInstrument;
import com.moyeorock.domain.user.enums.UserStatus;
import com.moyeorock.global.common.enums.Instrument;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserInstrumentRepository extends JpaRepository<UserInstrument, Long> {

    List<UserInstrument> findAllByUserIdOrderByIdAsc(Long userId);

    /**
     * 타 도메인 일괄 세션 조회용(UserService.getInstruments). IN 1번, 지정 상태(탈퇴)의 사용자는 제외.
     * user_instruments에는 status가 없고 User 연관관계도 안 쓰므로(architecture.md §5) users 서브쿼리로 거른다.
     * userId → id 순이라 순서를 유지한 채 그룹핑할 수 있다.
     */
    @Query("""
            select ui from UserInstrument ui
            where ui.userId in :userIds
              and ui.userId in (select u.id from User u where u.status <> :excludedStatus)
            order by ui.userId asc, ui.id asc
            """)
    List<UserInstrument> findAllByUserIdInAndUserStatusNotOrderByUserIdAscIdAsc(
            @Param("userIds") Collection<Long> userIds, @Param("excludedStatus") UserStatus excludedStatus);

    // 어떤 사용자 id와도 겹치지 않는 값(AUTO_INCREMENT는 1부터). 제외 목록이 비었을 때 NOT IN ()이 SQL 오류라 대신 넣는다.
    long NO_USER_ID = -1L;

    /**
     * 추천 후보 조회용(UserService.findRecommendationCandidates). 활동 중(ACTIVE)이고 추천을 허용한(is_recommendable) 사용자 중
     * 지정 악기를 가진 사람을 **무작위로** 최대 limit명 돌려준다. 제외할 사용자는 호출자가 넘긴다.
     * 앞에서부터 자르면 늦게 가입한 사용자가 후보에 영영 들어오지 못해서 무작위로 뽑는다(PR #151 리뷰).
     * 호출할 때마다 결과가 달라질 수 있다.
     */
    default List<UserInstrument> findRecommendationCandidates(Instrument instrument,
            Collection<Long> excludedUserIds, UserStatus status, int limit) {
        Collection<Long> excluded = excludedUserIds.isEmpty() ? List.of(NO_USER_ID) : excludedUserIds;
        return findRandomCandidates(instrument.name(), excluded, status.name(), limit);
    }

    // JPQL에는 무작위 정렬이 없어 MySQL 네이티브 쿼리를 쓴다. 엔티티 매핑은 그대로 적용된다.
    @Query(value = """
            select ui.* from user_instruments ui
            where ui.instrument = :instrument
              and ui.user_id not in (:excludedUserIds)
              and ui.user_id in (select u.id from users u where u.status = :status and u.is_recommendable = 1)
            order by rand()
            limit :limit
            """, nativeQuery = true)
    List<UserInstrument> findRandomCandidates(@Param("instrument") String instrument,
            @Param("excludedUserIds") Collection<Long> excludedUserIds, @Param("status") String status,
            @Param("limit") int limit);

    /**
     * 벌크 JPQL로 즉시 DELETE를 실행한다. 엔티티 remove 방식이면 Hibernate flush 순서(INSERT → DELETE)와
     * IDENTITY 즉시 INSERT 때문에 같은 instrument를 재삽입할 때 UNIQUE에 걸린다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UserInstrument ui where ui.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
