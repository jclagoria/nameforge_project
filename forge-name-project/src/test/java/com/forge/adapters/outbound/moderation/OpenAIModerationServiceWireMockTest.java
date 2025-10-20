package com.forge.adapters.outbound.moderation;

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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

@DisplayName("OpenAIModerationService WireMock Tests")
class OpenAIModerationServiceWireMockTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private OpenAIModerationService moderationService;
    private ModerationProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ModerationProperties();
        properties.getOpenai().setApiKey("test-api-key");
        properties.getOpenai().setEnabled(true);
        properties.getOpenai().setTimeout(Duration.ofSeconds(5));
        properties.getOpenai().setEndpoint(wireMock.baseUrl() + "/v1/moderations");

        WebClient webClient = WebClient.builder().build();

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

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("openai-moderation");
        Retry retry = retryRegistry.retry("openai-moderation");
        TimeLimiter timeLimiter = timeLimiterRegistry.timeLimiter("openai-moderation");

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

    @Test
    @DisplayName("Should Approve Clean Content With WireMock")
    void shouldApproveCleanContentWithWireMock() {
        // ARRANGE
        wireMock.stubFor(post(urlEqualTo("/v1/moderations"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer test-api-key"))
                .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(MediaType.APPLICATION_JSON_VALUE))
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
                            "hate/threatening": false,
                            "harassment": false,
                            "harassment/threatening": false,
                            "self-harm": false,
                            "self-harm/intent": false,
                            "self-harm/instructions": false,
                            "sexual": false,
                            "sexual/minors": false,
                            "violence": false,
                            "violence/graphic": false
                          },
                          "category_scores": {
                            "hate": 0.001,
                            "sexual": 0.001,
                            "violence": 0.001,
                            "harassment": 0.001,
                            "self-harm": 0.001
                          }
                        }
                      ]
                    }
                    """)));

        // ACT & ASSERT
        StepVerifier.create(moderationService.isAppropriate("cleanusername"))
                .expectNext(true)
                .verifyComplete();

        // Verify request was made
        wireMock.verify(1, postRequestedFor(urlEqualTo("/v1/moderations"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer test-api-key")));
    }

    @Test
    @DisplayName("Should Reject Inappropriate Content With WireMock")
    void shouldRejectInappropriateContentWithWireMock() {
        // ARRANGE
        wireMock.stubFor(post(urlEqualTo("/v1/moderations"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                    {
                      "id": "modr-456",
                      "model": "text-moderation-007",
                      "results": [
                        {
                          "flagged": true,
                          "categories": {
                            "hate": true,
                            "hate/threatening": false,
                            "harassment": false,
                            "harassment/threatening": false,
                            "self-harm": false,
                            "self-harm/intent": false,
                            "self-harm/instructions": false,
                            "sexual": false,
                            "sexual/minors": false,
                            "violence": false,
                            "violence/graphic": false
                          },
                          "category_scores": {
                            "hate": 0.95,
                            "sexual": 0.001,
                            "violence": 0.001,
                            "harassment": 0.001,
                            "self-harm": 0.001
                          }
                        }
                      ]
                    }
                    """)));

        // ACT & ASSERT
        StepVerifier.create(moderationService.isAppropriate("badcontent"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Handle Api Error With WireMock")
    void shouldHandleApiErrorWithWireMock() {
        // ARRANGE
        wireMock.stubFor(post(urlEqualTo("/v1/moderations"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        // ACT & ASSERT - Should fallback to SimpleModerationService
        StepVerifier.create(moderationService.isAppropriate("testuser"))
                .expectNext(true) // SimpleModerationService approves "testuser"
                .verifyComplete();
    }

    @Test
    void shouldHandleTimeoutWithWireMock() {
        // ARRANGE
        wireMock.stubFor(post(urlEqualTo("/v1/moderations"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(10000) // 10 seconds delay
                        .withBody("{}")));

        // ACT & ASSERT - Should timeout and fallback
        StepVerifier.create(moderationService.isAppropriate("testuser"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Handle Rate Limiting With WireMock")
    void shouldHandleRateLimitingWithWireMock() {
        // ARRANGE
        wireMock.stubFor(post(urlEqualTo("/v1/moderations"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader(HttpHeaders.RETRY_AFTER, "60")
                        .withBody("""
                    {
                      "error": {
                        "message": "Rate limit exceeded",
                        "type": "rate_limit_error"
                      }
                    }
                    """)));

        // ACT & ASSERT - Should fallback
        StepVerifier.create(moderationService.isAppropriate("testuser"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Handle Invalid Api Key With WireMock")
    void shouldHandleInvalidApiKeyWithWireMock() {
        // ARRANGE
        wireMock.stubFor(post(urlEqualTo("/v1/moderations"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withBody("""
                    {
                      "error": {
                        "message": "Invalid API key",
                        "type": "invalid_request_error"
                      }
                    }
                    """)));

        // ACT & ASSERT - Should fallback
        StepVerifier.create(moderationService.isAppropriate("testuser"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Handle Multiple Categories With WireMock")
    void shouldHandleMultipleCategoriesWithWireMock() {
        // ARRANGE
        wireMock.stubFor(post(urlEqualTo("/v1/moderations"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                    {
                      "id": "modr-789",
                      "model": "text-moderation-007",
                      "results": [
                        {
                          "flagged": true,
                          "categories": {
                            "hate": true,
                            "hate/threatening": false,
                            "harassment": true,
                            "harassment/threatening": false,
                            "self-harm": false,
                            "self-harm/intent": false,
                            "self-harm/instructions": false,
                            "sexual": true,
                            "sexual/minors": false,
                            "violence": false,
                            "violence/graphic": false
                          },
                          "category_scores": {
                            "hate": 0.85,
                            "sexual": 0.92,
                            "violence": 0.05,
                            "harassment": 0.78,
                            "self-harm": 0.01
                          }
                        }
                      ]
                    }
                    """)));

        // ACT & ASSERT
        StepVerifier.create(moderationService.isAppropriate("multibadcontent"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Verify Request Body With WireMock")
    void shouldVerifyRequestBodyWithWireMock() {
        // ARRANGE
        String username = "testusername123";

        wireMock.stubFor(post(urlEqualTo("/v1/moderations"))
                .withRequestBody(matchingJsonPath("$.input", equalTo(username)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                    {
                      "id": "modr-999",
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
        StepVerifier.create(moderationService.isAppropriate(username))
                .expectNext(true)
                .verifyComplete();

        // Verify exact request body
        wireMock.verify(postRequestedFor(urlEqualTo("/v1/moderations"))
                .withRequestBody(matchingJsonPath("$.input", equalTo(username))));
    }
}
