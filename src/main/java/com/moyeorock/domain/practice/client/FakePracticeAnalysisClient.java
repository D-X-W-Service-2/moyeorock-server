package com.moyeorock.domain.practice.client;

import com.moyeorock.domain.practice.config.PracticeProperties;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AI 서버가 생기기 전까지 쓰는 가짜 AI. 정해진 시간만큼 기다렸다가 고정 결과를 돌려준다 — 기다리는 이유는 dev 서버에서
 * 프론트가 SUBMITTED → ANALYZING → DONE 폴링을 실제처럼 붙여 볼 수 있게 하려는 것이다. practice.ai.type이 없으면 이게 뜬다.
 */
@Component
@ConditionalOnProperty(prefix = "practice.ai", name = "type", havingValue = "fake", matchIfMissing = true)
@RequiredArgsConstructor
public class FakePracticeAnalysisClient implements PracticeAnalysisClient {

    // 구간과 문장만 고정이고 세션은 요청한 세션 그대로 준다 — 혼자 연습한 세션 하나의 녹음이라서.
    private static final List<String> FIXED_LINES = List.of(
            "12,20,(테스트) 음정이 2도 플랫되었습니다",
            "45,58,(테스트) 박자가 조금 빠릅니다",
            "80,95,(테스트) 고음 구간에서 호흡이 짧습니다");

    private final PracticeProperties properties;

    @Override
    public List<AnalysisFeedback> analyze(AnalysisRequest request) {
        try {
            Thread.sleep(Duration.ofSeconds(properties.ai().fakeDelaySeconds()).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("가짜 AI 분석 대기 중 중단됨", e);
        }
        return FIXED_LINES.stream()
                .map(line -> line.split(",", 3))
                .map(cols -> new AnalysisFeedback(Integer.parseInt(cols[0]), Integer.parseInt(cols[1]),
                        request.part().name(), cols[2]))
                .toList();
    }
}
