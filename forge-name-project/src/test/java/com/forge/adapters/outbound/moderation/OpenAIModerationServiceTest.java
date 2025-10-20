package com.forge.adapters.outbound.moderation;

import com.forge.adapters.outbound.moderation.dto.ModerationResponse;
import com.forge.domain.ports.outboung.ModerationService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpenAI Moderation Service Tests")
class OpenAIModerationServiceTest {

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
    private SimpleModerationService fallbackService;
    private OpenAIModerationService moderationService;

    @BeforeEach
    void setUp() {
        properties = new ModerationProperties();
        properties.getOpenai().setApiKey("test-key");
        properties.getOpenai().setEnabled(true);
        properties.getOpenai().setTimeout(Duration.ofSeconds(5));

        fallbackService = new SimpleModerationService();

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

        circuitBreaker = circuitBreakerRegistry.circuitBreaker("openai-moderation-test");
        retry = retryRegistry.retry("openai-moderation-test");
        timeLimiter = timeLimiterRegistry.timeLimiter("openai-moderation-test");

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
    @DisplayName("Should approve clean content when OpenAI returns non-flagged response")
    void shouldApproveCleanContent() {
        // ARRANGE
        ModerationResponse response = createModerationResponse(false);
        setupWebClientMock(response);

        // ACT & ASSERT
        StepVerifier.create(moderationService.isAppropriate("cleanusername"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject flagged content when OpenAI returns flagged response")
    void shouldRejectFlaggedContent() {
        // ARRANGE
        ModerationResponse response = createModerationResponse(true);
        setupWebClientMock(response);

        // ACT & ASSERT
        StepVerifier.create(moderationService.isAppropriate("badcontent"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should fallback to simple service when OpenAI API returns server error")
    void shouldFallbackToSimpleServiceOnError() {
        // ARRANGE
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(ModerationResponse.class))
                .thenReturn(Mono.error(new WebClientResponseException(500, "Server Error", null, null, null)));

        // ACT & ASSERT
        StepVerifier.create(moderationService.isAppropriate("testuser"))
                .expectNext(true) // Falls back to SimpleModerationService
                .verifyComplete();
    }

    @Test
    @DisplayName("Should fallback to simple service when OpenAI API times out")
    void shouldHandleTimeout() {
        // ARRANGE
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(ModerationResponse.class))
                .thenReturn(Mono.delay(Duration.ofSeconds(10))
                        .then(Mono.just(createModerationResponse(false))));

        // ACT & ASSERT
        StepVerifier.create(moderationService.isAppropriate("testuser"))
                .expectNext(true) // Falls back on timeout
                .verifyComplete();
    }

    @Test
    @DisplayName("Should use simple service when OpenAI moderation is disabled")
    void shouldUseSimpleServiceWhenDisabled() {
        // ARRANGE
        properties.getOpenai().setEnabled(false);
        moderationService = new OpenAIModerationService(
                webClient,
                properties,
                fallbackService,
                circuitBreaker,
                retry,
                timeLimiter
        );

        // ACT & ASSERT
        StepVerifier.create(moderationService.isAppropriate("testuser"))
                .expectNext(true)
                .verifyComplete();

        verifyNoInteractions(webClient);
    }

    @Test
    @DisplayName("Should approve content when OpenAI returns null results")
    void shouldApproveWhenResultsAreNull() {
        // ARRANGE
        ModerationResponse response = new ModerationResponse();
        response.setId("modr-test");
        response.setModel("text-moderation-007");
        response.setResults(null); // Null results
        setupWebClientMock(response);

        // ACT & ASSERT
        StepVerifier.create(moderationService.isAppropriate("testuser"))
                .expectNext(true) // Defaults to appropriate
                .verifyComplete();
    }

    @Test
    @DisplayName("Should approve content when OpenAI returns empty results list")
    void shouldApproveWhenResultsAreEmpty() {
        // ARRANGE
        ModerationResponse response = new ModerationResponse();
        response.setId("modr-test");
        response.setModel("text-moderation-007");
        response.setResults(List.of()); // Empty list
        setupWebClientMock(response);

        // ACT & ASSERT
        StepVerifier.create(moderationService.isAppropriate("testuser"))
                .expectNext(true) // Defaults to appropriate
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject content when multiple moderation categories are flagged")
    void shouldRejectWhenMultipleCategoriesFlagged() {
        // ARRANGE
        ModerationResponse.Categories categories = new ModerationResponse.Categories();
        categories.setHate(true);
        categories.setSexual(true);
        categories.setViolence(true);

        ModerationResponse.ModerationResult result = new ModerationResponse.ModerationResult();
        result.setFlagged(true);
        result.setCategories(categories);

        ModerationResponse response = new ModerationResponse();
        response.setId("modr-test");
        response.setModel("text-moderation-007");
        response.setResults(List.of(result));

        setupWebClientMock(response);

        // ACT & ASSERT
        StepVerifier.create(moderationService.isAppropriate("offensive"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle null username input by delegating to OpenAI")
    void shouldHandleNullUsername() {
        // ARRANGE
        ModerationResponse response = createModerationResponse(false);
        setupWebClientMock(response);

        // ACT & ASSERT
        StepVerifier.create(moderationService.isAppropriate(null))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle empty username input by delegating to OpenAI")
    void shouldHandleEmptyUsername() {
        // ARRANGE
        ModerationResponse response = createModerationResponse(false);
        setupWebClientMock(response);

        // ACT & ASSERT
        StepVerifier.create(moderationService.isAppropriate(""))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should approve content when only one category is flagged but overall not flagged")
    void shouldApproveWhenCategoryFlaggedButOverallNot() {
        // ARRANGE
        ModerationResponse.Categories categories = new ModerationResponse.Categories();
        categories.setHate(false);
        categories.setSexual(false);
        categories.setViolence(false);

        ModerationResponse.ModerationResult result = new ModerationResponse.ModerationResult();
        result.setFlagged(false); // Overall not flagged
        result.setCategories(categories);

        ModerationResponse response = new ModerationResponse();
        response.setId("modr-test");
        response.setModel("text-moderation-007");
        response.setResults(List.of(result));

        setupWebClientMock(response);

        // ACT & ASSERT
        StepVerifier.create(moderationService.isAppropriate("borderline"))
                .expectNext(true)
                .verifyComplete();
    }

    private void setupWebClientMock(ModerationResponse response) {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(ModerationResponse.class))
                .thenReturn(Mono.just(response));
    }

    private ModerationResponse createModerationResponse(boolean flagged) {
        ModerationResponse.Categories categories = new ModerationResponse.Categories();
        categories.setHate(false);
        categories.setSexual(flagged);
        categories.setViolence(false);

        ModerationResponse.ModerationResult result = new ModerationResponse.ModerationResult();
        result.setFlagged(flagged);
        result.setCategories(categories);

        ModerationResponse response = new ModerationResponse();
        response.setId("modr-test");
        response.setModel("text-moderation-007");
        response.setResults(List.of(result));

        return response;
    }

}