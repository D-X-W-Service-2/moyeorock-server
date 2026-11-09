package com.moyeorock.domain.practice.repository;

import com.moyeorock.domain.practice.entity.PracticeFeedback;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PracticeFeedbackRepository extends JpaRepository<PracticeFeedback, Long> {

    // 상세의 타임라인 — 구간 시작 순, 같은 시작이면 저장 순. (recording_id, start_sec) 인덱스를 탄다.
    List<PracticeFeedback> findByRecordingIdOrderByStartSecAscIdAsc(Long recordingId);

    // 녹음 삭제(hard delete) 때 한 번에 지운다.
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PracticeFeedback f WHERE f.recordingId = :recordingId")
    int deleteByRecordingId(@Param("recordingId") Long recordingId);
}
