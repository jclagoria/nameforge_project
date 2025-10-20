package com.forge.adapters.outbound.moderation;

import com.forge.infrastructure.properties.ModerationProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

@DisplayName("Perspective API Integration Tests with WireMock")
public class PerspectiveApiModerationServiceWireMockTest {

    private static WireMockServer wireMockServer;
    private PerspectiveApiModerationService service;
    private ModerationProperties properties;

    @BeforeAll
    static void setupWireMock() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void tearDownWireMock() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();

        properties = new ModerationProperties();
        properties.getPerspective().setEnabled(true);
        properties.getPerspective().setApiKey("test-key");
        properties.getPerspective().setEndpoint("http://localhost:" + wireMockServer.port() + "/analyze");
        properties.getPerspective().setTimeout(Duration.ofSeconds(5));
        properties.getPerspective().setDefaultLanguage("es");
        properties.getPerspective().setDoNotStore(true);

        ModerationProperties.PerspectiveProperties.Thresholds thresholds =
                new ModerationProperties.PerspectiveProperties.Thresholds();
        thresholds.setToxicity(0.7);
        thresholds.setSevereToxicity(0.5);
        thresholds.setIdentityAttack(0.6);
        thresholds.setInsult(0.7);
        thresholds.setThreat(0.5);
        properties.getPerspective().setThresholds(thresholds);

        WebClient webClient = WebClient.builder().build();
        SimpleModerationService fallbackService = new SimpleModerationService();

        // Create real Resilience4j instances
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

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("perspective-moderation-wiremock-test");
        Retry retry = retryRegistry.retry("perspective-moderation-wiremock-test");
        TimeLimiter timeLimiter = timeLimiterRegistry.timeLimiter("perspective-moderation-wiremock-test");

        service = new PerspectiveApiModerationService(
                webClient, properties, fallbackService, circuitBreaker, retry, timeLimiter
        );
    }

    @Test
    @DisplayName("Should successfully analyze appropriate username")
    void shouldAnalyzeAppropriateUsername() {
        // Given
        String responseBody = """
                {
                  "attributeScores": {
                    "TOXICITY": {
                      "summaryScore": {"value": 0.3, "type": "PROBABILITY"}
                    },
                    "SEVERE_TOXICITY": {
                      "summaryScore": {"value": 0.1, "type": "PROBABILITY"}
                    },
                    "IDENTITY_ATTACK": {
                      "summaryScore": {"value": 0.2, "type": "PROBABILITY"}
                    },
                    "INSULT": {
                      "summaryScore": {"value": 0.2, "type": "PROBABILITY"}
                    },
                    "THREAT": {
                      "summaryScore": {"value": 0.1, "type": "PROBABILITY"}
                    }
                  },
                  "languages": ["es"]
                }
                """;

        stubFor(post(urlPathEqualTo("/analyze"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)));

        // When & Then
        StepVerifier.create(service.isAppropriate("gooduser"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should detect toxic username")
    void shouldDetectToxicUsername() {
        // Given
        String responseBody = """
                {
                  "attributeScores": {
                    "TOXICITY": {
                      "summaryScore": {"value": 0.95, "type": "PROBABILITY"}
                    },
                    "SEVERE_TOXICITY": {
                      "summaryScore": {"value": 0.6, "type": "PROBABILITY"}
                    },
                    "IDENTITY_ATTACK": {
                      "summaryScore": {"value": 0.8, "type": "PROBABILITY"}
                    },
                    "INSULT": {
                      "summaryScore": {"value": 0.85, "type": "PROBABILITY"}
                    },
                    "THREAT": {
                      "summaryScore": {"value": 0.2, "type": "PROBABILITY"}
                    }
                  },
                  "languages": ["es"]
                }
                """;

        stubFor(post(urlPathEqualTo("/analyze"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)));

        // When & Then
        StepVerifier.create(service.isAppropriate("baduser"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle API timeout gracefully")
    void shouldHandleTimeout() {
        // Given
        stubFor(post(urlPathEqualTo("/analyze"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(10000))); // Delay longer than timeout

        // When & Then
        StepVerifier.create(service.isAppropriate("testuser"))
                .expectNext(true) // Should fallback to simple service
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle API error response")
    void shouldHandleApiError() {
        // Given
        stubFor(post(urlPathEqualTo("/analyze"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        // When & Then
        StepVerifier.create(service.isAppropriate("testuser"))
                .expectNext(true) // Should fallback to simple service
                .verifyComplete();
    }

}
