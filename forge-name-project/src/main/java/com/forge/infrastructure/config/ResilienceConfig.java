package com.forge.infrastructure.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

/**
 * Resilience4j Configuration for Circuit Breaker, Retry, and TimeLimiter patterns.
 * <p>
 * This configuration creates specific instances for different external dependencies:
 * <ul>
 *   <li><b>openai-moderation:</b> Circuit breaker and retry for OpenAI API</li>
 *   <li><b>database:</b> Circuit breaker and retry for R2DBC PostgreSQL</li>
 *   <li><b>redis-cache:</b> Circuit breaker and retry for Redis cache</li>
 * </ul>
 * </p>
 *
 * <h3>Why separate instances?</h3>
 * Each external dependency has different:
 * <ul>
 *   <li>Failure tolerance thresholds</li>
 *   <li>Retry strategies</li>
 *   <li>Timeout requirements</li>
 *   <li>Recovery time expectations</li>
 * </ul>
 */
@Slf4j
@Configuration
public class ResilienceConfig {

    // ============================================================
    // Circuit Breaker Beans
    // ============================================================

    @Bean
    public CircuitBreaker openaiModerationCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker circuitBreaker = registry.circuitBreaker("openai-moderation");

        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.warn("OpenAI Circuit Breaker state: {} -> {}",
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()))
                .onFailureRateExceeded(event -> log.error("OpenAI failure rate exceeded: {}%",
                        event.getFailureRate()))
                .onError(event -> log.debug("OpenAI circuit breaker error: {}",
                        event.getThrowable().getMessage()));

        return circuitBreaker;
    }

    @Bean
    public CircuitBreaker databaseCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker circuitBreaker = registry.circuitBreaker("database");

        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.error("Database Circuit Breaker state: {} -> {}",
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()))
                .onFailureRateExceeded(event -> log.error("Database failure rate exceeded: {}%",
                        event.getFailureRate()));

        return circuitBreaker;
    }

    @Bean
    public CircuitBreaker redisCacheCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker circuitBreaker = registry.circuitBreaker("redis-cache");

        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.warn("Redis Circuit Breaker state: {} -> {}",
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()))
                .onFailureRateExceeded(event -> log.warn("Redis failure rate exceeded: {}%",
                        event.getFailureRate()));

        return circuitBreaker;
    }

    @Bean
    public CircuitBreaker perspectiveModerationCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker circuitBreaker = registry.circuitBreaker("perspective-moderation");

        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.warn("Perspective Circuit Breaker state: {} -> {}",
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()))
                .onFailureRateExceeded(event -> log.error("Perspective failure rate exceeded: {}%",
                        event.getFailureRate()))
                .onError(event -> log.debug("Perspective circuit breaker error: {}",
                        event.getThrowable().getMessage()));

        return circuitBreaker;
    }

    // ============================================================
    // Retry Beans
    // ============================================================

    @Bean
    public Retry openaiModerationRetry(RetryRegistry registry) {
        Retry retry = registry.retry("openai-moderation");

        retry.getEventPublisher()
                .onRetry(event -> log.warn("OpenAI retry attempt {} - Error: {}",
                        event.getNumberOfRetryAttempts(),
                        event.getLastThrowable().getMessage()))
                .onSuccess(event -> log.debug("OpenAI retry succeeded after {} attempts",
                        event.getNumberOfRetryAttempts()));

        return retry;
    }

    @Bean
    public Retry databaseRetry(RetryRegistry registry) {
        Retry retry = registry.retry("database");

        retry.getEventPublisher()
                .onRetry(event -> log.warn("Database retry attempt {} - Error: {}",
                        event.getNumberOfRetryAttempts(),
                        event.getLastThrowable().getMessage()))
                .onSuccess(event -> log.debug("Database retry succeeded after {} attempts",
                        event.getNumberOfRetryAttempts()));

        return retry;
    }

    @Bean
    public Retry redisCacheRetry(RetryRegistry registry) {
        Retry retry = registry.retry("redis-cache");

        retry.getEventPublisher()
                .onRetry(event -> log.debug("Redis retry attempt {} - Error: {}",
                        event.getNumberOfRetryAttempts(),
                        event.getLastThrowable().getMessage()));

        return retry;
    }

    @Bean
    public Retry perspectiveModerationRetry(RetryRegistry registry) {
        Retry retry = registry.retry("perspective-moderation");

        retry.getEventPublisher()
                .onRetry(event -> log.warn("Perspective retry attempt {} - Error: {}",
                        event.getNumberOfRetryAttempts(),
                        event.getLastThrowable().getMessage()))
                .onSuccess(event -> log.debug("Perspective retry succeeded after {} attempts",
                        event.getNumberOfRetryAttempts()));

        return retry;
    }

    // ============================================================
    // TimeLimiter Beans
    // ============================================================

    @Bean
    public TimeLimiter openaiModerationTimeLimiter(TimeLimiterRegistry registry) {
        TimeLimiter timeLimiter = registry.timeLimiter("openai-moderation");

        timeLimiter.getEventPublisher()
                .onTimeout(event -> log.warn("OpenAI moderation timeout exceeded"))
                .onSuccess(event -> log.debug("OpenAI moderation completed within timeout"));

        return timeLimiter;
    }

    @Bean
    public TimeLimiter databaseTimeLimiter(TimeLimiterRegistry registry) {
        TimeLimiter timeLimiter = registry.timeLimiter("database");

        timeLimiter.getEventPublisher()
                .onTimeout(event -> log.error("Database operation timeout exceeded"))
                .onSuccess(event -> log.debug("Database operation completed within timeout"));

        return timeLimiter;
    }

    @Bean
    public TimeLimiter redisCacheTimeLimiter(TimeLimiterRegistry registry) {
        TimeLimiter timeLimiter = registry.timeLimiter("redis-cache");

        timeLimiter.getEventPublisher()
                .onTimeout(event -> log.warn("Redis cache timeout exceeded"));

        return timeLimiter;
    }

    @Bean
    public TimeLimiter perspectiveModerationTimeLimiter(TimeLimiterRegistry registry) {
        TimeLimiter timeLimiter = registry.timeLimiter("perspective-moderation");

        timeLimiter.getEventPublisher()
                .onTimeout(event -> log.warn("Perspective moderation timeout exceeded"))
                .onSuccess(event -> log.debug("Perspective moderation completed within timeout"));

        return timeLimiter;
    }

    // ============================================================
    // Custom Configuration (Programmatic - alternative to YAML)
    // ============================================================

    /**
     * Example of programmatic configuration (alternative to YAML).
     * Useful when you need dynamic configuration or complex predicates.
     */
    public CircuitBreakerConfig customCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                // Custom predicate for recording failures
                .recordException(throwable -> {
                    // Only record as failure if it's a server error or network issue
                    if (throwable instanceof WebClientResponseException webClientEx) {
                        int status = webClientEx.getStatusCode().value();
                        return status >= 500; // Only 5xx are circuit breaker failures
                    }
                    return throwable instanceof java.io.IOException
                            || throwable instanceof java.util.concurrent.TimeoutException;
                })
                // Don't record client errors as failures
                .ignoreException(throwable -> {
                    if (throwable instanceof WebClientResponseException webClientEx) {
                        int status = webClientEx.getStatusCode().value();
                        return status >= 400 && status < 500; // Ignore 4xx
                    }
                    return throwable instanceof IllegalArgumentException
                            || throwable instanceof IllegalStateException;
                })
                .build();
    }

    /**
     * Example of programmatic retry configuration.
     */
    public RetryConfig customRetryConfig() {
        return RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .intervalFunction(io.github.resilience4j.core.IntervalFunction
                        .ofExponentialBackoff(500, 2)) // 500ms, 1s, 2s
                // Custom predicate for retryable exceptions
                .retryOnException(throwable -> {
                    if (throwable instanceof WebClientResponseException webClientEx) {
                        int status = webClientEx.getStatusCode().value();
                        // Retry on 5xx, but not on 4xx
                        return status >= 500;
                    }
                    return throwable instanceof java.io.IOException
                            || throwable instanceof java.util.concurrent.TimeoutException;
                })
                .build();
    }

    /**
     * Example of programmatic time limiter configuration.
     */
    public TimeLimiterConfig customTimeLimiterConfig() {
        return TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(10))
                .cancelRunningFuture(true)
                .build();
    }
}
