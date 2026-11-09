package com.moyeorock.domain.practice.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.domain.practice.config.PracticeProperties;
import com.moyeorock.global.common.enums.Instrument;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FakePracticeAnalysisClientTest {

    @Test
    @DisplayName("가짜 AI는 고정 구간·문장 3줄을 요청한 세션 그대로 돌려준다(혼자 연습한 세션 하나의 녹음)")
    void analyze_returnsFixedLinesWithRequestedPart() {
        FakePracticeAnalysisClient client =
                new FakePracticeAnalysisClient(new PracticeProperties(30, new PracticeProperties.Ai("fake", 0)));

        List<AnalysisFeedback> result = client.analyze(new AnalysisRequest("https://signed", "m4a", Instrument.BASS));

        assertThat(result).hasSize(3)
                .allSatisfy(feedback -> {
                    assertThat(feedback.part()).isEqualTo("BASS");
                    assertThat(feedback.content()).startsWith("(테스트)");
                    assertThat(feedback.endSec()).isGreaterThan(feedback.startSec());
                });
    }
}
