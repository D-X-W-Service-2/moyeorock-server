package com.moyeorock.domain.user.repository;

import com.moyeorock.domain.user.entity.UserInstrument;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserInstrumentRepository extends JpaRepository<UserInstrument, Long> {

    List<UserInstrument> findAllByUserIdOrderByIdAsc(Long userId);

    /** 타 도메인 일괄 세션 조회용(UserService.getInstruments). IN 1번. userId → id 순이라 순서를 유지한 채 그룹핑할 수 있다. */
    List<UserInstrument> findAllByUserIdInOrderByUserIdAscIdAsc(Collection<Long> userIds);

    /**
     * 벌크 JPQL로 즉시 DELETE를 실행한다. 엔티티 remove 방식이면 Hibernate flush 순서(INSERT → DELETE)와
     * IDENTITY 즉시 INSERT 때문에 같은 instrument를 재삽입할 때 UNIQUE에 걸린다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UserInstrument ui where ui.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
