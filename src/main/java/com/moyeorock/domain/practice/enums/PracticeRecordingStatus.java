package com.moyeorock.domain.practice.enums;

import java.util.Set;

// 연습 녹음 AI 분석 상태(erd.md §19). 진행률은 없고 이 4가지로만 보여 준다.
public enum PracticeRecordingStatus {
    SUBMITTED,   // 전송 완료 — 요청을 받아 저장함. AI에 보내기 전(대기)
    ANALYZING,   // 분석 중
    DONE,        // 분석 완료 — 피드백이 저장됨
    FAILED;      // 분석 실패 — 다시 요청할 수 있다

    // 진행 중인 상태. 한 사람당 이 상태인 녹음은 하나뿐이다(practice_recordings.active_user_id 유니크와 같은 기준).
    public static final Set<PracticeRecordingStatus> IN_PROGRESS = Set.of(SUBMITTED, ANALYZING);
}
