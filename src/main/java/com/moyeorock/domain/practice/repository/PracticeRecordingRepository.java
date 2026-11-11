package com.moyeorock.domain.practice.repository;

import com.moyeorock.domain.practice.entity.PracticeRecording;
import com.moyeorock.domain.practice.enums.PracticeRecordingStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PracticeRecordingRepository extends JpaRepository<PracticeRecording, Long> {

    // 정렬은 Service가 Pageable에 고정해 넣는다(최근 요청 순).
    Page<PracticeRecording> findByUserId(Long userId, Pageable pageable);

    boolean existsByUserIdAndStatusIn(Long userId, Collection<PracticeRecordingStatus> statuses);

    /**
     * 쓰기 전용 잠금 조회. 삭제와 분석 상태 전이(시작·완료·실패)·다시 요청이 모두 이걸로 시작해 같은 녹음에서 차례로 처리된다 —
     * 삭제가 먼저면 늦게 온 분석 결과가 빈 결과를 보고 버려지고, 결과 저장이 먼저면 삭제가 그 결과까지 지운다(고아 피드백 없음).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM PracticeRecording r WHERE r.id = :id")
    Optional<PracticeRecording> findByIdForUpdate(@Param("id") Long id);

    // 회원 탈퇴 때 이 사람 녹음 전부를 잠금 조회한다 — findByIdForUpdate와 같은 이유로, 겹친 분석 완료가 탈퇴 커밋 뒤 빈 결과를 보고 버린다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM PracticeRecording r WHERE r.userId = :userId")
    List<PracticeRecording> findAllByUserIdForUpdate(@Param("userId") Long userId);

    /**
     * 부팅 전에 마지막으로 바뀐 진행 중 녹음을 실패로 바꾼다(서버 재시작 정리). 웹 서버가 부팅 정리보다 먼저 요청을 받기 시작해서,
     * 부팅 뒤에 들어온 요청까지 실패로 만들지 않도록 updated_at으로 거른다. updated_at은 엔티티 감사가 JVM 시각으로 찍으므로
     * 여기서도 DB 시각(ON UPDATE CURRENT_TIMESTAMP)에 맡기지 않고 직접 넣는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE PracticeRecording r
            SET r.status = :failed, r.failureReason = :reason, r.updatedAt = :now
            WHERE r.status IN :unfinished AND r.updatedAt < :bootedAt
            """)
    int failUnfinishedBefore(@Param("bootedAt") LocalDateTime bootedAt, @Param("reason") String reason,
            @Param("now") LocalDateTime now, @Param("failed") PracticeRecordingStatus failed,
            @Param("unfinished") Collection<PracticeRecordingStatus> unfinished);
}
