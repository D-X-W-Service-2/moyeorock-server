package com.moyeorock.domain.user.repository;

import com.moyeorock.domain.user.entity.UserInstrument;
import com.moyeorock.domain.user.enums.UserStatus;
import com.moyeorock.global.common.enums.Instrument;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
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

    /**
     * 추천 후보 조회용(UserService.findRecommendationCandidates). 활동 중(ACTIVE)이고 추천을 허용한(is_recommendable) 사용자 중
     * 지정 악기를 가진 사람을 userId 순으로 돌려준다. 제외할 사용자는 호출자가 넘긴다. 개수 상한은 Pageable로 건다.
     */
    @Query("""
            select ui from UserInstrument ui
            where ui.instrument = :instrument
              and ui.userId not in :excludedUserIds
              and ui.userId in (select u.id from User u where u.status = :status and u.isRecommendable = true)
            order by ui.userId asc
            """)
    List<UserInstrument> findRecommendationCandidates(@Param("instrument") Instrument instrument,
            @Param("excludedUserIds") Collection<Long> excludedUserIds, @Param("status") UserStatus status,
            Pageable pageable);

    /**
     * 벌크 JPQL로 즉시 DELETE를 실행한다. 엔티티 remove 방식이면 Hibernate flush 순서(INSERT → DELETE)와
     * IDENTITY 즉시 INSERT 때문에 같은 instrument를 재삽입할 때 UNIQUE에 걸린다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UserInstrument ui where ui.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
