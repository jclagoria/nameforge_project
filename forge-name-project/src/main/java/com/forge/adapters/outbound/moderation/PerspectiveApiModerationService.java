package com.forge.adapters.outbound.moderation;

import com.forge.adapters.outbound.moderation.dto.PerspectiveRequest;
import com.forge.adapters.outbound.moderation.dto.PerspectiveResponse;
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
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Improved Perspective API Moderation Service with intelligent error handling and resilience patterns.
 * Uses Google's Perspective Comment Analyzer API to detect toxic content.
 * <p>
 * Features:
 * <ul>
 *   <li>Reactive Circuit Breaker - prevents cascading failures</li>
 *   <li>Intelligent Retry - only retries recoverable errors (5xx, network, timeouts)</li>
 *   <li>Rate Limit Handling - special treatment for 429 errors</li>
 *   <li>Timeout Management - separate from retries</li>
 *   <li>Graceful Fallback - simple moderation when Perspective API fails</li>
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
@Primary
@ConditionalOnProperty(prefix = "moderation.perspective", name = "enabled", havingValue = "true")
public class PerspectiveApiModerationService implements ModerationService {

    private final WebClient webClient;
    private final ModerationProperties properties;
    private final SimpleModerationService fallbackService;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final TimeLimiter timeLimiter;

    // Attributes to analyze for toxicity
    private static final String TOXICITY = "TOXICITY";
    private static final String SEVERE_TOXICITY = "SEVERE_TOXICITY";
    private static final String IDENTITY_ATTACK = "IDENTITY_ATTACK";
    private static final String INSULT = "INSULT";
    private static final String THREAT = "THREAT";

    public PerspectiveApiModerationService(
            WebClient moderationWebClient,
            ModerationProperties properties,
            SimpleModerationService fallbackService,
            CircuitBreaker perspectiveModerationCircuitBreaker,
            Retry perspectiveModerationRetry,
            TimeLimiter perspectiveModerationTimeLimiter
    ) {
        this.webClient = moderationWebClient;
        this.properties = properties;
        this.fallbackService = fallbackService;
        this.circuitBreaker = perspectiveModerationCircuitBreaker;
        this.retry = perspectiveModerationRetry;
        this.timeLimiter = perspectiveModerationTimeLimiter;

        log.info("Perspective API Moderation Service initialized with enhanced resilience patterns");
    }

    @Override
    public Mono<Boolean> isAppropriate(String username) {
        if (!properties.getPerspective().isEnabled()) {
            log.debug("Perspective API disabled, using fallback service");
            return fallbackService.isAppropriate(username);
        }

        return callPerspectiveApiWithResilience(username)
                .doOnSuccess(result -> log.debug("Username '{}' moderation result: {}", username, result))
                .onErrorResume(this::handleError);
    }

    /**
     * Calls Perspective API with full resilience stack:
     * TimeLimiter → Retry → CircuitBreaker → API Call
     */
    private Mono<Boolean> callPerspectiveApiWithResilience(String username) {
        return callPerspectiveApi(username)
                // 1. Circuit Breaker - prevents cascading failures
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                // 2. Retry - intelligent retry on recoverable errors
                .transformDeferred(RetryOperator.of(retry))
                // 3. Time Limiter - overall timeout for the operation
                .transformDeferred(TimeLimiterOperator.of(timeLimiter))
                .doOnError(throwable -> {
                    String errorType = ErrorClassification.classifyError(throwable);
                    log.warn("Perspective API error [{}]: {}", errorType, throwable.getMessage());
                });
    }

    /**
     * Core API call to Perspective API endpoint.
     */
    private Mono<Boolean> callPerspectiveApi(String username) {
        PerspectiveRequest request = buildRequest(username);
        String apiUrl = properties.getPerspective().getEndpoint()
                + "?key=" + properties.getPerspective().getApiKey();

        return webClient.post()
                .uri(apiUrl)
                .header("Content-Type", "application/json")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(PerspectiveResponse.class)
                // Individual request timeout (shorter than TimeLimiter)
                .timeout(Duration.ofSeconds(8))
                .map(response -> evaluateResponse(response, username))
                .doOnError(WebClientResponseException.class, this::logHttpError)
                .doOnError(WebClientException.class, this::logNetworkError);
    }

    /**
     * Builds the Perspective API request payload.
     */
    private PerspectiveRequest buildRequest(String username) {
        Map<String, String> requestedAttributes = Map.of(
                TOXICITY, "{}",
                SEVERE_TOXICITY, "{}",
                IDENTITY_ATTACK, "{}",
                INSULT, "{}",
                THREAT, "{}"
        );

        return PerspectiveRequest.builder()
                .comment(PerspectiveRequest.Comment.builder()
                        .text(username)
                        .build())
                .languages(List.of(properties.getPerspective().getDefaultLanguage()))
                .requestedAttributes(requestedAttributes)
                .doNotStore(properties.getPerspective().isDoNotStore())
                .build();
    }

    /**
     * Evaluates the API response against configured thresholds.
     */
    private Boolean evaluateResponse(PerspectiveResponse response, String username) {
        if (response.getAttributeScores() == null || response.getAttributeScores().isEmpty()) {
            log.warn("Empty attribute scores in Perspective API response, defaulting to appropriate");
            return true;
        }

        ModerationProperties.PerspectiveProperties.Thresholds thresholds =
                properties.getPerspective().getThresholds();

        boolean isToxic = checkAttribute(response, TOXICITY, thresholds.getToxicity(), username);
        boolean isSevereToxic = checkAttribute(response, SEVERE_TOXICITY, thresholds.getSevereToxicity(), username);
        boolean isIdentityAttack = checkAttribute(response, IDENTITY_ATTACK, thresholds.getIdentityAttack(), username);
        boolean isInsult = checkAttribute(response, INSULT, thresholds.getInsult(), username);
        boolean isThreat = checkAttribute(response, THREAT, thresholds.getThreat(), username);

        boolean isAppropriated = !isToxic && !isSevereToxic && !isIdentityAttack && !isInsult && !isThreat;

        if (!isAppropriated) {
            log.info("Username '{}' flagged by Perspective API - Scores: " +
                            "TOXICITY={}, SEVERE_TOXICITY={}, IDENTITY_ATTACK={}, INSULT={}, THREAT={}",
                    username,
                    getScore(response, TOXICITY),
                    getScore(response, SEVERE_TOXICITY),
                    getScore(response, IDENTITY_ATTACK),
                    getScore(response, INSULT),
                    getScore(response, THREAT));
        }

        return isAppropriated;
    }

    /**
     * Checks if a specific attribute exceeds its threshold.
     */
    private boolean checkAttribute(PerspectiveResponse response, String attributeName,
                                   Double threshold, String username) {
        PerspectiveResponse.AttributeScore attributeScore = response.getAttributeScores().get(attributeName);

        if (attributeScore == null || attributeScore.getSummaryScore() == null) {
            log.debug("No {} score available for username '{}'", attributeName, username);
            return false;
        }

        Double score = attributeScore.getSummaryScore().getValue();

        if (score == null) {
            log.debug("Null {} score value for username '{}'", attributeName, username);
            return false;
        }

        return score > threshold;
    }

    /**
     * Extracts the score value for a specific attribute.
     * Used for logging purposes.
     */
    private Double getScore(PerspectiveResponse response, String attributeName) {
        PerspectiveResponse.AttributeScore attributeScore = response.getAttributeScores().get(attributeName);

        if (attributeScore != null && attributeScore.getSummaryScore() != null) {
            return attributeScore.getSummaryScore().getValue();
        }

        return 0.0;
    }

    /**
     * Handles errors with intelligent fallback strategy.
     */
    private Mono<Boolean> handleError(Throwable error) {
        String errorType = ErrorClassification.classifyError(error);

        // Special handling for rate limit errors
        if (ErrorClassification.isRateLimitError(error)) {
            log.warn("Perspective API rate limit reached [429], falling back to simple moderation");
            return fallbackService.isAppropriate("rate-limited")
                    .doOnNext(result -> log.info("Fallback service used due to rate limit"));
        }

        // Circuit breaker open - use fallback immediately
        if (error.getMessage() != null && error.getMessage().contains("CircuitBreaker")) {
            log.warn("Circuit breaker OPEN for Perspective API, using fallback service");
            return fallbackService.isAppropriate("circuit-open")
                    .doOnNext(result -> log.info("Fallback service used due to circuit breaker"));
        }

        // For all other errors, use fallback
        log.error("Perspective API failed [{}], using fallback service: {}",
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
            log.error("Perspective API server error ({}) - Body: {}", status, body);
        } else {
            log.warn("Perspective API client error ({}) - Body: {}", status, body);
        }
    }

    /**
     * Logs network and connectivity errors.
     */
    private void logNetworkError(WebClientException error) {
        String cause = error.getCause() != null ? error.getCause().getMessage() : "Unknown";
        log.error("Network error calling Perspective API: {} - Cause: {}", error.getMessage(), cause);
    }
}
