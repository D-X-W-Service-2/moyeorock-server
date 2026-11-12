package com.moyeorock.domain.practice.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.moyeorock.domain.practice.config.PracticeProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 실제 AI 연습 피드백 호출({@code POST {base-url}/v1/rehearsals/feedback}, AI 팀 핸드오프 2026-11-11).
 * {@code practice.ai.type=ai}일 때만 뜬다. 곡 동기화·팀원 추천과 같은 AI 서버·키(AI_SERVER_BASE_URL·AI_SERVER_API_KEY)를 쓴다.
 *
 * <p>원곡은 AI 서버가 비공개 저장소에서 읽으므로 곡의 external_id만 보내고, 연습 녹음은 Base64로 실어 보낸다. 응답의
 * {@code events}(녹음에서 원곡과 다르게 연주한 후보 구간)를 피드백 한 줄씩으로 바꾼다.
 *
 * <p>사용자에게 보여 줄 사유가 있는 실패(지원하지 않는 곡·원곡과 맞추지 못함·녹음 길이 등)는
 * {@link PracticeAnalysisException}, 그 밖(인증 실패·5xx·응답 해석 실패)은 원래 예외를 그대로 던진다.
 */
@Component
@ConditionalOnProperty(prefix = "practice.ai", name = "type", havingValue = "ai")
public class AiPracticeAnalysisClient implements PracticeAnalysisClient {

    static final String FEEDBACK_PATH = "/v1/rehearsals/feedback";
    // 지원하지 않는 곡이면 AI가 404와 이 문장을 준다. 주소를 잘못 넣어 난 404와 구분하려고 문장까지 본다.
    static final String UNSUPPORTED_SONG_MESSAGE = "Feedback is not available for this song";

    private static final String STATUS_ANALYZED = "analyzed";
    private static final String STATUS_REVIEW_REQUIRED = "review_required";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final PracticeProperties.Ai properties;
    private final RestClient restClient;

    @Autowired
    public AiPracticeAnalysisClient(PracticeProperties properties) {
        this(properties.ai(), RestClient.builder().requestFactory(requestFactory(properties.ai())).build());
    }

    // 테스트용: MockRestServiceServer를 바인딩한 RestClient 주입
    AiPracticeAnalysisClient(PracticeProperties.Ai properties, RestClient restClient) {
        // type=ai로 켰는데 주소·키가 없으면 분석마다 실패한다 — 배포 설정 누락을 부팅 때 바로 드러낸다.
        if (!StringUtils.hasText(properties.baseUrl()) || !StringUtils.hasText(properties.apiKey())) {
            throw new IllegalStateException("practice.ai.type=ai면 AI_SERVER_BASE_URL·AI_SERVER_API_KEY가 필요합니다.");
        }
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public List<AnalysisFeedback> analyze(AnalysisRequest request) {
        FeedbackResponse response = call(new FeedbackRequest(request.songExternalId(), request.part().name(),
                Base64.getEncoder().encodeToString(request.performanceAudio()), request.performanceFilename()));
        if (response == null || response.status() == null) {
            throw new IllegalStateException("AI 연습 피드백 응답에 status가 없습니다.");
        }
        if (STATUS_REVIEW_REQUIRED.equals(response.status())) {
            throw new PracticeAnalysisException(PracticeAnalysisException.REVIEW_REQUIRED);
        }
        if (!STATUS_ANALYZED.equals(response.status())) {
            throw new IllegalStateException("AI 연습 피드백 응답의 status를 알 수 없습니다: " + response.status());
        }
        String part = request.part().name();
        return Optional.ofNullable(response.events()).orElse(List.of()).stream()
                .filter(Objects::nonNull)
                .filter(event -> event.start() != null && event.end() != null)
                .map(event -> new AnalysisFeedback(
                        (int) Math.floor(event.start()), (int) Math.ceil(event.end()), part, contentOf(event)))
                .toList();
    }

    private FeedbackResponse call(FeedbackRequest body) {
        try {
            return restClient.post()
                    .uri(feedbackUri())
                    .headers(headers -> headers.setBearerAuth(properties.apiKey()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(FeedbackResponse.class);
        } catch (HttpClientErrorException e) {
            throw clientErrorOf(e);
        } catch (HttpServerErrorException.ServiceUnavailable e) {
            // 원곡 저장소 장애 — 다시 요청하면 될 수 있다
            throw new PracticeAnalysisException(PracticeAnalysisException.AI_UNAVAILABLE, e);
        }
    }

    // 404(지원하지 않는 곡)와 녹음 문제(길이 3~60초·형식·크기 — AI가 400·413·422로 거절)만 사용자 사유로 바꾼다.
    // 401·403(키 문제)이나 문장이 다른 404(주소 문제)는 우리 설정 문제라 그대로 던진다 — 일반 사유가 된다.
    private static RuntimeException clientErrorOf(HttpClientErrorException e) {
        int status = e.getStatusCode().value();
        if (status == HttpStatus.NOT_FOUND.value() && e.getResponseBodyAsString().contains(UNSUPPORTED_SONG_MESSAGE)) {
            return new PracticeAnalysisException(PracticeAnalysisException.UNSUPPORTED_SONG, e);
        }
        if (status == HttpStatus.BAD_REQUEST.value() || status == HttpStatus.CONTENT_TOO_LARGE.value()
                || status == HttpStatus.UNPROCESSABLE_CONTENT.value()) {
            return new PracticeAnalysisException(PracticeAnalysisException.INVALID_RECORDING, e);
        }
        return e;
    }

    // 주소 끝에 /가 있어도 //가 되지 않게 붙인다. AI 팀 안내대로 base-url에는 /v1을 넣지 않는다.
    private URI feedbackUri() {
        return UriComponentsBuilder.fromUriString(properties.baseUrl()).path(FEEDBACK_PATH).build().toUri();
    }

    // AI 이벤트 종류와 대응하는 원곡 구간을 함께 보여 준다. 종류가 없거나 새로 추가되면 일반 문장으로 처리한다.
    private static String contentOf(Event event) {
        String detail = switch (event.type() == null ? "" : event.type()) {
            case "pitch_or_harmony_mismatch" -> "음정 또는 화음이 원곡과 다른 구간이에요.";
            case "rhythm_mismatch" -> "리듬이 원곡과 다른 구간이에요.";
            case "missing_or_quiet" -> "연주가 빠졌거나 소리가 작은 구간이에요.";
            default -> "원곡과 다르게 연주한 구간이에요.";
        };
        if (event.referenceStart() == null || event.referenceEnd() == null) {
            return detail;
        }
        return "원곡 %s~%s: %s".formatted(
                minuteSecond((int) Math.floor(event.referenceStart())),
                minuteSecond((int) Math.ceil(event.referenceEnd())), detail);
    }

    private static String minuteSecond(int totalSeconds) {
        return "%d:%02d".formatted(totalSeconds / 60, totalSeconds % 60);
    }

    // 첫 호출은 AWS 초기 실행 때문에 수십 초가 걸린다(실측 약 45초). 읽기 대기 상한은 AI 팀 권장값(180초)이 기본이다.
    private static JdkClientHttpRequestFactory requestFactory(PracticeProperties.Ai properties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(properties.readTimeoutSeconds()));
        return factory;
    }

    record FeedbackRequest(String externalId, String part, String performanceAudioBase64,
                           String performanceFilename) {
    }

    // alignment_similarity·alignment_method·transposition_semitones·sensitivity·score는 저장할 곳이 없어 받지 않는다.
    record FeedbackResponse(String status, List<Event> events) {
    }

    // start·end는 녹음 시간, reference_start·reference_end는 대응하는 원곡 시간(초, 소수).
    record Event(Double start, Double end, String type,
                 @JsonProperty("reference_start") Double referenceStart,
                 @JsonProperty("reference_end") Double referenceEnd) {
    }
}
