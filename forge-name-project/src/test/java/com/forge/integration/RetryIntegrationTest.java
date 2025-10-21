package com.forge.integration;

import com.forge.adapters.outbound.moderation.OpenAIModerationService;
import com.forge.adapters.outbound.moderation.SimpleModerationService;
import com.forge.infrastructure.properties.ModerationProperties;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
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
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;
import wiremock.com.google.common.net.HttpHeaders;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2: Retry Integration Tests
 * <p>
 * Tests the retry behavior of the application with real OpenAI moderation service integration.
 * These tests validate:
 * <ul>
 *   <li>Retry succeeds after transient failures</li>
 *   <li>Retry exhausts attempts and fails</li>
 *   <li>Retry does NOT apply to 4xx client errors</li>
 *   <li>Retry uses exponential backoff</li>
 *   <li>Retry + Circuit Breaker interaction</li>
 * </ul>
 * </p>
 *
 * @see <a href="docs/Resilience-Testing-Analysis.md">Resilience Testing Analysis - Phase 2</a>
 */
@DisplayName("Retry Integration Tests - Phase 2")
class RetryIntegrationTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private OpenAIModerationService moderationService;
    private ModerationProperties properties;
    private Retry retry;
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        // Reset WireMock
        wireMock.resetAll();

        // Configure properties
        properties = new ModerationProperties();
        properties.getOpenai().setApiKey("test-api-key");
        properties.getOpenai().setEnabled(true);
        properties.getOpenai().setTimeout(Duration.ofSeconds(5));
        properties.getOpenai().setEndpoint(wireMock.baseUrl() + "/v1/moderations");

        WebClient webClient = WebClient.builder().build();

        // Circuit Breaker with aggressive configuration for tests
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(2))
                .permittedNumberOfCallsInHalfOpenState(2)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        // Retry configuration with fast backoff for tests
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(100))
                .intervalFunction(io.github.resilience4j.core.IntervalFunction
                        .ofExponentialBackoff(100, 2)) // 100ms, 200ms, 400ms
                .retryOnException(throwable -> {
                    // Retry on 5xx errors
                    if (throwable instanceof org.springframework.web.reactive.function.client.WebClientResponseException webClientEx) {
                        int status = webClientEx.getStatusCode().value();
                        return status >= 500;
                    }
                    return throwable instanceof java.io.IOException
                            || throwable instanceof java.util.concurrent.TimeoutException;
                })
                .build();

        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(10))
                .build();

        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
        TimeLimiterRegistry timeLimiterRegistry = TimeLimiterRegistry.of(timeLimiterConfig);

        circuitBreaker = circuitBreakerRegistry.circuitBreaker("openai-moderation-test");
        retry = retryRegistry.retry("openai-moderation-test");
        TimeLimiter timeLimiter = timeLimiterRegistry.timeLimiter("openai-moderation-test");

        SimpleModerationService fallbackService = new SimpleModerationService();

        moderationService = new OpenAIModerationService(
                webClient,
                properties,
                fallbackService,
                circuitBreaker,
                retry,
                timeLimiter
        );
    }

    /**
     * Test 1: Retry succeeds after single transient failure
     * <p>
     * Scenario: First attempt fails with 500, second attempt succeeds with 200
     * Expected: Final result is success, 2 requests made (1 original + 1 retry)
     * </p>
     */
    @Test
    @DisplayName("Should succeed after single transient failure with retry")
    void shouldSucceedAfterTransientFailure() {
        // ARRANGE - First request fails (500), second succeeds (200)
        wireMock.stubFor(post(urlEqualTo("/v1/moderations"))
                .inScenario("transient-failure")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error"))
                .willSetStateTo("Failed Once"));

        wireMock.stubFor(post(urlEqualTo("/v1/moderations"))
                .inScenario("transient-failure")
                .whenScenarioStateIs("Failed Once")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                            {
                              "id": "modr-123",
                              "model": "text-moderation-007",
                              "results": [
                                {
                                  "flagged": false,
                                  "categories": {
                                    "hate": false,
                                    "sexual": false,
                                    "violence": false
                                  }
                                }
                              ]
                            }
                            """)));

        // ACT & ASSERT
        StepVerifier.create(moderationService.isAppropriate("cleanusername"))
                .expectNext(true)
                .verifyComplete();

        // Verify exactly 2 requests were made (1 original + 1 retry)
        wireMock.verify(2, postRequestedFor(urlEqualTo("/v1/moderations")));

        // Verify retry metrics
        assertThat(retry.getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt())
                .as("Should have 1 successful call with retry")
                .isEqualTo(1);
    }

    /**
     * Test 2: Retry exhausts attempts and fails
     * <p>
     * Scenario: All retry attempts fail (500 responses)
     * Expected: Final result uses fallback, maxAttempts requests made
     * </p>
     */
    @Test
    @DisplayName("Should fail after exhausting all retry attempts")
    void shouldFailAfterExhaustingRetries() {
        // ARRANGE - All attempts fail with 500
        wireMock.stubFor(post(urlEqualTo("/v1/moderations"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        // ACT & ASSERT - Should fallback to SimpleModerationService
        StepVerifier.create(moderationService.isAppropriate("testuser"))
                .expectNext(true) // SimpleModerationService approves "testuser"
                .verifyComplete();

        // Verify exactly 3 requests were made (1 original + 2 retries)
        wireMock.verify(3, postRequestedFor(urlEqualTo("/v1/moderations")));

        // Verify retry metrics
        assertThat(retry.getMetrics().getNumberOfFailedCallsWithRetryAttempt())
                .as("Should have 1 failed call with max retry attempts")
                .isEqualTo(1);
    }

    /**
     * Test 3: Retry does NOT apply to 4xx client errors
     * <p>
     * Scenario: First request fails with 400 Bad Request
     * Expected: No retry is attempted, only 1 request made
     * </p>
     */
    @Test
    @DisplayName("Should not retry on client errors 4xx")
    void shouldNotRetryOnClientErrors() {
        // ARRANGE - Fail with 400 Bad Request (client error)
        wireMock.stubFor(post(urlEqualTo("/v1/moderations"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withBody("""
                            {
                              "error": {
                                "message": "Invalid request format",
                                "type": "invalid_request_error"
                              }
                            }
                            """)));

        // ACT & ASSERT - Should fallback immediately without retry
        StepVerifier.create(moderationService.isAppropriate("testuser"))
                .expectNext(true) // SimpleModerationService approves
                .verifyComplete();

        // Verify only 1 request was made (NO retries for 4xx)
        wireMock.verify(1, postRequestedFor(urlEqualTo("/v1/moderations")));

        // Verify retry was not attempted
        assertThat(retry.getMetrics().getNumberOfFailedCallsWithRetryAttempt())
                .as("Should have no retry attempts for 4xx errors")
                .isEqualTo(0);
    }

    /**
     * Test 4: Retry with exponential backoff
     * <p>
     * Scenario: Multiple failures trigger exponential backoff between retries
     * Expected: Backoff intervals increase between retries (100ms, 200ms, 400ms)
     * </p>
     */
    @Test
    @DisplayName("Should apply exponential backoff between retry attempts")
    void shouldApplyExponentialBackoff() {
        // ARRANGE - All attempts fail to measure backoff
        wireMock.stubFor(post(urlEqualTo("/v1/moderations"))
                .willReturn(aResponse()
                        .withStatus(503) // Service Unavailable
                        .withBody("Service temporarily unavailable")));

        // ACT - Measure time taken for all retries
        long startTime = System.currentTimeMillis();

        StepVerifier.create(moderationService.isAppropriate("testuser"))
                .expectNext(true) // Fallback after exhausting retries
                .verifyComplete();

        long duration = System.currentTimeMillis() - startTime;

        // ASSERT - Total duration should reflect exponential backoff
        // Expected: ~100ms + ~200ms + ~400ms = ~700ms
        // However, reactive execution may be faster, so we verify minimum backoff occurred
        assertThat(duration)
                .as("Total retry duration should show exponential backoff occurred (min 300ms for 3 retries)")
                .isGreaterThanOrEqualTo(300L); // Minimum: 100ms + 200ms for first 2 backoffs

        // Verify 3 requests were made
        wireMock.verify(3, postRequestedFor(urlEqualTo("/v1/moderations")));

        // Verify retry metrics show retries occurred
        assertThat(retry.getMetrics().getNumberOfFailedCallsWithRetryAttempt())
                .as("Should have recorded retry attempts")
                .isEqualTo(1);
    }

    /**
     * Test 5: Retry + Circuit Breaker interaction
     * <p>
     * Scenario: Multiple operations where retry exhausts attempts, accumulating failures
     * Expected: Circuit breaker accumulates failures and opens after threshold
     * </p>
     */
    @Test
    @DisplayName("Should open circuit breaker after retries exhaust multiple times")
    void shouldOpenCircuitAfterRetriesExhaust() {
        // ARRANGE - All attempts fail
        wireMock.stubFor(post(urlEqualTo("/v1/moderations"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        // ACT - Execute multiple operations to accumulate failures
        // Each operation will retry 3 times and fail, counting as 1 failure for circuit breaker
        // Circuit breaker config: slidingWindowSize=5, failureRateThreshold=50%
        // After 3 failures out of 5 calls, circuit should open
        int requestCount = 0;
        for (int i = 0; i < 5; i++) {
            StepVerifier.create(moderationService.isAppropriate("testuser" + i))
                    .expectNext(true) // Fallback after retry exhaustion or circuit open
                    .verifyComplete();

            // Count requests before circuit opens
            if (circuitBreaker.getState() != CircuitBreaker.State.OPEN) {
                requestCount += 3; // Each failed operation makes 3 requests (1 original + 2 retries)
            }
        }

        // ASSERT - Circuit breaker should be OPEN after failure threshold exceeded
        assertThat(circuitBreaker.getState())
                .as("Circuit breaker should be OPEN after multiple retry failures")
                .isEqualTo(CircuitBreaker.State.OPEN);

        // Verify circuit breaker metrics
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        assertThat(metrics.getNumberOfFailedCalls())
                .as("Should have accumulated failures from retry exhaustions")
                .isGreaterThanOrEqualTo(3); // Minimum calls to trigger circuit breaker

        assertThat(metrics.getFailureRate())
                .as("Failure rate should exceed threshold (50%)")
                .isGreaterThan(50.0f);

        // Verify that some HTTP requests were made before circuit opened
        // (Not all 15 because circuit opens partway through)
        assertThat(wireMock.findAll(postRequestedFor(urlEqualTo("/v1/moderations"))).size())
                .as("HTTP requests should have been made before circuit opened")
                .isGreaterThanOrEqualTo(3); // At least 1 failed operation with 3 retries
    }

    /**
     * Test 6 (Bonus): Verify retry does not retry on 429 Too Many Requests
     * <p>
     * Scenario: API returns 429 rate limit error
     * Expected: No retry (rate limits should be respected), only 1 request made
     * </p>
     */
    @Test
    @DisplayName("Should not retry on 429 Too Many Requests")
    void shouldNotRetryOnRateLimitError() {
        // ARRANGE - Fail with 429 Too Many Requests
        wireMock.stubFor(post(urlEqualTo("/v1/moderations"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", "60")
                        .withBody("""
                            {
                              "error": {
                                "message": "Rate limit exceeded",
                                "type": "rate_limit_error"
                              }
                            }
                            """)));

        // ACT & ASSERT - Should fallback immediately without retry
        StepVerifier.create(moderationService.isAppropriate("testuser"))
                .expectNext(true) // SimpleModerationService approves
                .verifyComplete();

        // Verify only 1 request was made (NO retries for 429)
        wireMock.verify(1, postRequestedFor(urlEqualTo("/v1/moderations")));

        // Verify retry was not attempted
        assertThat(retry.getMetrics().getNumberOfFailedCallsWithRetryAttempt())
                .as("Should have no retry attempts for 429 rate limit errors")
                .isEqualTo(0);
    }

    /**
     * Test 7 (Bonus): Verify retry success after multiple transient failures
     * <p>
     * Scenario: First 2 attempts fail (500), third attempt succeeds (200)
     * Expected: Final result is success, 3 requests made (1 original + 2 retries)
     * </p>
     */
    @Test
    @DisplayName("Should succeed after multiple transient failures")
    void shouldSucceedAfterMultipleTransientFailures() {
        // ARRANGE - First 2 requests fail, third succeeds
        AtomicInteger attemptCount = new AtomicInteger(0);

        wireMock.stubFor(post(urlEqualTo("/v1/moderations"))
                .willReturn(aResponse()
                        .withTransformer("response-template", "attemptCount", attemptCount)
                        .withTransformerParameter("attemptCount", attemptCount)));

        wireMock.stubFor(post(urlEqualTo("/v1/moderations"))
                .inScenario("multiple-failures")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error"))
                .willSetStateTo("Failed Once"));

        wireMock.stubFor(post(urlEqualTo("/v1/moderations"))
                .inScenario("multiple-failures")
                .whenScenarioStateIs("Failed Once")
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error"))
                .willSetStateTo("Failed Twice"));

        wireMock.stubFor(post(urlEqualTo("/v1/moderations"))
                .inScenario("multiple-failures")
                .whenScenarioStateIs("Failed Twice")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                            {
                              "id": "modr-456",
                              "model": "text-moderation-007",
                              "results": [
                                {
                                  "flagged": false,
                                  "categories": {
                                    "hate": false,
                                    "sexual": false,
                                    "violence": false
                                  }
                                }
                              ]
                            }
                            """)));

        // ACT & ASSERT
        StepVerifier.create(moderationService.isAppropriate("resilientusername"))
                .expectNext(true)
                .verifyComplete();

        // Verify exactly 3 requests were made (1 original + 2 retries)
        wireMock.verify(3, postRequestedFor(urlEqualTo("/v1/moderations")));

        // Verify retry metrics
        assertThat(retry.getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt())
                .as("Should have 1 successful call after 2 retries")
                .isEqualTo(1);
    }
}
