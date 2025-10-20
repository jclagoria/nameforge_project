package com.forge.infrastructure.resilience;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

/**
 * Utility class for classifying errors as retryable or non-retryable.
 * <p>
 * This class helps Resilience4j determine which errors should trigger retries
 * and which should be treated as permanent failures.
 * </p>
 *
 * <h3>Classification Strategy:</h3>
 * <ul>
 *   <li><b>Retryable:</b> Network issues, timeouts, server errors (5xx)</li>
 *   <li><b>Non-Retryable:</b> Client errors (4xx), validation errors, business logic errors</li>
 *   <li><b>Special Cases:</b> 429 (rate limit), 503 (unavailable), timeouts</li>
 * </ul>
 */
@Slf4j
@UtilityClass
public class ErrorClassification {

    /**
     * Determines if an error should be retried.
     *
     * @param throwable The error to classify
     * @return true if the error is recoverable and should be retried
     */
    public static boolean isRetryable(Throwable throwable) {
        if (throwable == null) {
            return false;
        }

        // Network and I/O errors are retryable
        if (isNetworkError(throwable)) {
            log.debug("Network error detected (retryable): {}", throwable.getMessage());
            return true;
        }

        // Timeout errors are retryable
        if (isTimeoutError(throwable)) {
            log.debug("Timeout error detected (retryable): {}", throwable.getMessage());
            return true;
        }

        // WebClient response errors need special handling
        if (throwable instanceof WebClientResponseException webClientEx) {
            return isRetryableHttpStatus(webClientEx.getStatusCode().value());
        }

        // Check cause if wrapped
        if (throwable.getCause() != null && throwable.getCause() != throwable) {
            return isRetryable(throwable.getCause());
        }

        // By default, unknown errors are not retryable (fail-safe)
        log.debug("Non-retryable error: {}", throwable.getClass().getSimpleName());
        return false;
    }

    /**
     * Determines if an HTTP status code represents a retryable error.
     *
     * @param statusCode HTTP status code
     * @return true if the status represents a temporary, retryable error
     */
    public static boolean isRetryableHttpStatus(int statusCode) {
        // 5xx server errors are generally retryable
        if (statusCode >= 500) {
            // Special cases
            return switch (statusCode) {
                case 501 -> false;  // Not Implemented - won't change with retry
                case 505 -> false;  // HTTP Version Not Supported - won't change
                case 511 -> false;  // Network Authentication Required - needs user action
                default -> true;    // 500, 502, 503, 504 and others are retryable
            };
        }

        // 4xx client errors are generally NOT retryable
        if (statusCode >= 400 && statusCode < 500) {
            return switch (statusCode) {
                case 408 -> true;   // Request Timeout - can retry
                case 423 -> true;   // Locked - temporary state, might unlock
                case 429 -> false;  // Too Many Requests - needs backoff, not simple retry
                default -> false;   // 400, 401, 403, 404, 422, etc. - client errors
            };
        }

        // 3xx redirects - let WebClient handle
        // 2xx success - no retry needed
        return false;
    }

    /**
     * Checks if error is a network connectivity issue.
     */
    private static boolean isNetworkError(Throwable throwable) {
        return throwable instanceof IOException
                || throwable instanceof ConnectException
                || throwable.getClass().getName().contains("UnknownHostException")
                || throwable.getClass().getName().contains("SocketException")
                || throwable.getClass().getName().contains("SocketTimeoutException");
    }

    /**
     * Checks if error is a timeout.
     */
    private static boolean isTimeoutError(Throwable throwable) {
        return throwable instanceof TimeoutException
                || throwable.getClass().getName().contains("TimeoutException")
                || (throwable.getMessage() != null && throwable.getMessage().toLowerCase().contains("timeout"));
    }

    /**
     * Determines if the error should open the circuit breaker.
     * <p>
     * This is similar to retryable, but may have different thresholds.
     * For example, 429 errors shouldn't retry immediately, but also shouldn't
     * open the circuit since the service is working (just rate-limited).
     * </p>
     */
    public static boolean shouldRecordFailure(Throwable throwable) {
        if (throwable instanceof WebClientResponseException webClientEx) {
            int status = webClientEx.getStatusCode().value();

            // Don't record client errors (4xx) as circuit breaker failures
            if (status >= 400 && status < 500) {
                // Exception: 429 rate limit might indicate service degradation
                // but shouldn't open circuit immediately
                return status == 429;
            }

            // Record all 5xx as failures
            return status >= 500;
        }

        // Record network and timeout errors
        return isNetworkError(throwable) || isTimeoutError(throwable);
    }

    /**
     * Determines if error is a rate limit error that needs special handling.
     */
    public static boolean isRateLimitError(Throwable throwable) {
        if (throwable instanceof WebClientResponseException webClientEx) {
            return webClientEx.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS;
        }
        return false;
    }

    /**
     * Gets a human-readable classification of the error.
     */
    public static String classifyError(Throwable throwable) {
        if (throwable == null) {
            return "UNKNOWN";
        }

        if (isRateLimitError(throwable)) {
            return "RATE_LIMIT";
        }

        if (isTimeoutError(throwable)) {
            return "TIMEOUT";
        }

        if (isNetworkError(throwable)) {
            return "NETWORK";
        }

        if (throwable instanceof WebClientResponseException webClientEx) {
            int status = webClientEx.getStatusCode().value();
            if (status >= 500) return "SERVER_ERROR";
            if (status >= 400) return "CLIENT_ERROR";
            return "HTTP_" + status;
        }

        return throwable.getClass().getSimpleName();
    }
}
