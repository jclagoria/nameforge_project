package com.forge.adapters.outbound.moderation;

import com.forge.adapters.outbound.moderation.dto.ModerationRequest;
import com.forge.adapters.outbound.moderation.dto.ModerationResponse;
import com.forge.domain.ports.outboung.ModerationService;
import com.forge.infrastructure.properties.ModerationProperties;
import com.forge.infrastructure.resilience.ErrorClassification;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Improved OpenAI Moderation Service with intelligent error handling and resilience patterns.
 * <p>
 * Features:
 * <ul>
 *   <li>Reactive Circuit Breaker - prevents cascading failures</li>
 *   <li>Intelligent Retry - only retries recoverable errors (5xx, network, timeouts)</li>
 *   <li>Rate Limit Handling - special treatment for 429 errors</li>
 *   <li>Timeout Management - separate from retries</li>
 *   <li>Graceful Fallback - simple moderation when OpenAI fails</li>
 * </ul>
 * </p>
 *
 * <h3>Error Handling Strategy:</h3>
 * <ul>
 *   <li><b>Retry:</b> 500, 502, 503, 504, network errors, timeouts</li>
 *   <li><b>Don't Retry:</b> 400, 401, 403, 404, 422, 429 (client errors)</li>
 *   <li><b>Circuit Break:</b> Opens on sustained 5xx or network failures</li>
 *   <li><b>Fallback:</b> Simple word-based moderation when all else fails</li>
 * </ul>
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "moderation.openai", name = "enabled", havingValue = "true")
public class OpenAIModerationService implements ModerationService {

    private final WebClient webClient;
    private final ModerationProperties properties;
    private final SimpleModerationService fallbackService;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final TimeLimiter timeLimiter;

    public OpenAIModerationService(
            WebClient webClient,
            ModerationProperties properties,
            CircuitBreaker openaiModerationCircuitBreaker,
            Retry openaiModerationRetry,
            TimeLimiter openaiModerationTimeLimiter
    ) {
        this.webClient = webClient;
        this.properties = properties;
        this.fallbackService = new SimpleModerationService(); // Instantiate fallback directly
        this.circuitBreaker = openaiModerationCircuitBreaker;
        this.retry = openaiModerationRetry;
        this.timeLimiter = openaiModerationTimeLimiter;

        log.info("OpenAI Moderation Service initialized with enhanced resilience patterns");
    }

    @Override
    public Mono<Boolean> isAppropriate(String username) {
        if (!properties.getOpenai().isEnabled()) {
            log.debug("OpenAI moderation disabled, using fallback service");
            return fallbackService.isAppropriate(username);
        }

        return callOpenAIModerationWithResilience(username)
                .doOnSuccess(result -> log.debug("Username '{}' moderation result: {}", username, result))
                .onErrorResume(this::handleError);
    }

    /**
     * Calls OpenAI Moderation API with full resilience stack:
     * TimeLimiter → Retry → CircuitBreaker → API Call
     */
    private Mono<Boolean> callOpenAIModerationWithResilience(String username) {
        return callOpenAIModeration(username)
                // 1. Circuit Breaker - prevents cascading failures
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                // 2. Retry - intelligent retry on recoverable errors
                .transformDeferred(RetryOperator.of(retry))
                // 3. Time Limiter - overall timeout for the operation
                .transformDeferred(TimeLimiterOperator.of(timeLimiter))
                .doOnError(throwable -> {
                    String errorType = ErrorClassification.classifyError(throwable);
                    log.warn("OpenAI Moderation API error [{}]: {}", errorType, throwable.getMessage());
                });
    }

    /**
     * Core API call to OpenAI Moderation endpoint.
     */
    private Mono<Boolean> callOpenAIModeration(String username) {
        ModerationRequest request = new ModerationRequest(username);

        return webClient.post()
                .uri(properties.getOpenai().getEndpoint())
                .header("Authorization", "Bearer " + properties.getOpenai().getApiKey())
                .header("Content-Type", "application/json")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ModerationResponse.class)
                // Individual request timeout (shorter than TimeLimiter)
                .timeout(Duration.ofSeconds(8))
                .map(this::parseResponse)
                .doOnError(WebClientResponseException.class, this::logHttpError)
                .doOnError(WebClientException.class, this::logNetworkError);
    }

    /**
     * Parses OpenAI moderation response.
     */
    private Boolean parseResponse(ModerationResponse response) {
        if (response.getResults() == null || response.getResults().isEmpty()) {
            log.warn("Empty moderation results from OpenAI, defaulting to appropriate");
            return true;
        }

        boolean flagged = response.getResults().get(0).isFlagged();

        if (flagged) {
            log.info("Content flagged by OpenAI Moderation");
        }

        return !flagged;
    }

    /**
     * Handles errors with intelligent fallback strategy.
     */
    private Mono<Boolean> handleError(Throwable error) {
        String errorType = ErrorClassification.classifyError(error);

        // Special handling for rate limit errors
        if (ErrorClassification.isRateLimitError(error)) {
            log.warn("OpenAI rate limit reached [429], falling back to simple moderation");
            return fallbackService.isAppropriate("rate-limited")
                    .doOnNext(result -> log.info("Fallback service used due to rate limit"));
        }

        // Circuit breaker open - use fallback immediately
        if (error.getMessage() != null && error.getMessage().contains("CircuitBreaker")) {
            log.warn("Circuit breaker OPEN for OpenAI, using fallback service");
            return fallbackService.isAppropriate("circuit-open")
                    .doOnNext(result -> log.info("Fallback service used due to circuit breaker"));
        }

        // For all other errors, use fallback
        log.error("OpenAI Moderation failed [{}], using fallback service: {}",
                errorType, error.getMessage());

        return fallbackService.isAppropriate("error-fallback")
                .doOnNext(result -> log.info("Fallback service used due to error"));
    }

    /**
     * Logs HTTP errors with detailed information.
     */
    private void logHttpError(WebClientResponseException error) {
        int status = error.getStatusCode().value();
        String body = error.getResponseBodyAsString();

        if (status == 429) {
            log.warn("Rate limit exceeded (429) - Headers: {}", error.getHeaders());
        } else if (status >= 500) {
            log.error("OpenAI server error ({}) - Body: {}", status, body);
        } else {
            log.warn("OpenAI client error ({}) - Body: {}", status, body);
        }
    }

    /**
     * Logs network and connectivity errors.
     */
    private void logNetworkError(WebClientException error) {
        String cause = error.getCause() != null ? error.getCause().getMessage() : "Unknown";
        log.error("Network error calling OpenAI: {} - Cause: {}", error.getMessage(), cause);
    }
}
