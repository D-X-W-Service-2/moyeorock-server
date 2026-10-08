package com.moyeorock.domain.user.repository;

import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.enums.UserStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByIdAndStatus(Long id, UserStatus status);

    /**
     * 쓰기 전용 잠금 조회. 온보딩·세션 교체처럼 user_instruments를 지우고 다시 넣는 요청이
     * 같은 사용자에게 동시에 오면 UNIQUE(user_id, instrument)에 걸려 500이 나므로 행 잠금으로 직렬화한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id and u.status = :status")
    Optional<User> findByIdAndStatusForUpdate(@Param("id") Long id, @Param("status") UserStatus status);

    boolean existsByNickname(String nickname);

    // auth 도메인용 (architecture.md §3: auth → user 단방향, AuthService의 UserRepository 사용 허용)
    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findByKakaoId(String kakaoId);

    Page<User> findByStatusAndNicknameContaining(UserStatus status, String nickname, Pageable pageable);

    /** 타 도메인 일괄 요약 조회용(UserService.getSummaries). IN 1번, 지정 상태(탈퇴)만 제외. */
    List<User> findAllByIdInAndStatusNot(Collection<Long> ids, UserStatus status);
}
