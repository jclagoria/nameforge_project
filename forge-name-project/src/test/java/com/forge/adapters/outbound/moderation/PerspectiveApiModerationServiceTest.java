package com.forge.adapters.outbound.moderation;

import com.forge.adapters.outbound.moderation.dto.PerspectiveResponse;
import com.forge.infrastructure.properties.ModerationProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Perspective API Moderation Service Tests")
class PerspectiveApiModerationServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private CircuitBreaker circuitBreaker;
    private Retry retry;
    private TimeLimiter timeLimiter;

    private ModerationProperties properties;
    private PerspectiveApiModerationService service;

    @BeforeEach
    void setUp() {
        properties = new ModerationProperties();
        properties.getPerspective().setEnabled(true);
        properties.getPerspective().setApiKey("test-api-key");
        properties.getPerspective().setTimeout(Duration.ofSeconds(10));
        properties.getPerspective().setDefaultLanguage("es");
        properties.getPerspective().setDoNotStore(true);

        // Set default thresholds
        ModerationProperties.PerspectiveProperties.Thresholds thresholds =
                new ModerationProperties.PerspectiveProperties.Thresholds();
        thresholds.setToxicity(0.7);
        thresholds.setSevereToxicity(0.5);
        thresholds.setIdentityAttack(0.6);
        thresholds.setInsult(0.7);
        thresholds.setThreat(0.5);
        properties.getPerspective().setThresholds(thresholds);

        // Create real Resilience4j instances (not mocks) because reactive operators need real config
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build();

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(100))
                .build();

        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(10))
                .build();

        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
        TimeLimiterRegistry timeLimiterRegistry = TimeLimiterRegistry.of(timeLimiterConfig);

        circuitBreaker = circuitBreakerRegistry.circuitBreaker("perspective-moderation-test");
        retry = retryRegistry.retry("perspective-moderation-test");
        timeLimiter = timeLimiterRegistry.timeLimiter("perspective-moderation-test");

        service = new PerspectiveApiModerationService(
                webClient, properties, circuitBreaker, retry, timeLimiter
        );
    }

    @Test
    @DisplayName("Should return true for appropriate username (low toxicity scores)")
    void shouldReturnTrueForAppropriateUsername() {
        // Given
        String username = "cooluser123";
        PerspectiveResponse response = createResponse(0.3, 0.2, 0.1, 0.2, 0.1);

        setupWebClientMock(response);

        // When & Then
        StepVerifier.create(service.isAppropriate(username))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return false for toxic username (high toxicity score)")
    void shouldReturnFalseForToxicUsername() {
        // Given
        String username = "offensiveuser";
        PerspectiveResponse response = createResponse(0.9, 0.3, 0.2, 0.3, 0.1);

        setupWebClientMock(response);

        // When & Then
        StepVerifier.create(service.isAppropriate(username))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return false for username with severe toxicity")
    void shouldReturnFalseForSevereToxicity() {
        // Given
        String username = "extremeuser";
        PerspectiveResponse response = createResponse(0.5, 0.8, 0.2, 0.3, 0.1);

        setupWebClientMock(response);

        // When & Then
        StepVerifier.create(service.isAppropriate(username))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return false for username with identity attack")
    void shouldReturnFalseForIdentityAttack() {
        // Given
        String username = "attackuser";
        PerspectiveResponse response = createResponse(0.5, 0.3, 0.8, 0.3, 0.1);

        setupWebClientMock(response);

        // When & Then
        StepVerifier.create(service.isAppropriate(username))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should fallback to simple service on API error")
    void shouldFallbackOnApiError() {
        // Given
        String username = "testuser";

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(PerspectiveResponse.class))
                .thenReturn(Mono.error(new WebClientResponseException(500, "Internal Server Error",
                        null, null, null)));

        // When & Then - Falls back to SimpleModerationService
        StepVerifier.create(service.isAppropriate("validuser"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should use fallback service when Perspective API is disabled")
    void shouldUseFallbackWhenDisabled() {
        // Given
        properties.getPerspective().setEnabled(false);
        String username = "validuser";

        // When & Then - Uses SimpleModerationService directly
        StepVerifier.create(service.isAppropriate(username))
                .expectNext(true)
                .verifyComplete();

        verifyNoInteractions(webClient);
    }


    // Helper methods

    private void setupWebClientMock(PerspectiveResponse response) {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(PerspectiveResponse.class))
                .thenReturn(Mono.just(response));
    }

    private PerspectiveResponse createResponse(double toxicity, double severeToxicity,
                                               double identityAttack, double insult, double threat) {
        PerspectiveResponse response = new PerspectiveResponse();

        Map<String, PerspectiveResponse.AttributeScore> scores = Map.of(
                "TOXICITY", createAttributeScore(toxicity),
                "SEVERE_TOXICITY", createAttributeScore(severeToxicity),
                "IDENTITY_ATTACK", createAttributeScore(identityAttack),
                "INSULT", createAttributeScore(insult),
                "THREAT", createAttributeScore(threat)
        );

        response.setAttributeScores(scores);

        return response;
    }

    private PerspectiveResponse.AttributeScore createAttributeScore(double value) {
        PerspectiveResponse.Score score = new PerspectiveResponse.Score(value, "PROBABILITY");
        PerspectiveResponse.AttributeScore attributeScore = new PerspectiveResponse.AttributeScore();
        attributeScore.setSummaryScore(score);
        return attributeScore;
    }

}