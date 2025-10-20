package com.forge.infrastructure.resilience;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorClassificationTest {

    @Test
    void shouldClassifyNetworkErrorsAsRetryable() {
        // Given
        IOException ioException = new IOException("Connection refused");
        ConnectException connectException = new ConnectException("Network unreachable");

        // When & Then
        assertThat(ErrorClassification.isRetryable(ioException)).isTrue();
        assertThat(ErrorClassification.isRetryable(connectException)).isTrue();
    }

    @Test
    void shouldClassifyTimeoutErrorsAsRetryable() {
        // Given
        TimeoutException timeoutException = new TimeoutException("Request timed out");

        // When & Then
        assertThat(ErrorClassification.isRetryable(timeoutException)).isTrue();
    }

    @Test
    void shouldClassify5xxErrorsAsRetryable() {
        // Given
        WebClientResponseException serverError = WebClientResponseException.create(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                null, null, null
        );

        WebClientResponseException badGateway = WebClientResponseException.create(
                HttpStatus.BAD_GATEWAY.value(),
                "Bad Gateway",
                null, null, null
        );

        WebClientResponseException serviceUnavailable = WebClientResponseException.create(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "Service Unavailable",
                null, null, null
        );

        // When & Then
        assertThat(ErrorClassification.isRetryable(serverError)).isTrue();
        assertThat(ErrorClassification.isRetryable(badGateway)).isTrue();
        assertThat(ErrorClassification.isRetryable(serviceUnavailable)).isTrue();
    }

    @Test
    void shouldClassify4xxErrorsAsNonRetryable() {
        // Given
        WebClientResponseException badRequest = WebClientResponseException.create(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                null, null, null
        );

        WebClientResponseException unauthorized = WebClientResponseException.create(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                null, null, null
        );

        WebClientResponseException notFound = WebClientResponseException.create(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                null, null, null
        );

        // When & Then
        assertThat(ErrorClassification.isRetryable(badRequest)).isFalse();
        assertThat(ErrorClassification.isRetryable(unauthorized)).isFalse();
        assertThat(ErrorClassification.isRetryable(notFound)).isFalse();
    }

    @Test
    void shouldClassify429RateLimitAsNonRetryable() {
        // Given
        WebClientResponseException tooManyRequests = WebClientResponseException.create(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Too Many Requests",
                null, null, null
        );

        // When & Then
        assertThat(ErrorClassification.isRetryable(tooManyRequests)).isFalse();
        assertThat(ErrorClassification.isRateLimitError(tooManyRequests)).isTrue();
    }

    @Test
    void shouldClassify408RequestTimeoutAsRetryable() {
        // Given
        WebClientResponseException requestTimeout = WebClientResponseException.create(
                HttpStatus.REQUEST_TIMEOUT.value(),
                "Request Timeout",
                null, null, null
        );

        // When & Then
        assertThat(ErrorClassification.isRetryable(requestTimeout)).isTrue();
    }

    @Test
    void shouldRecordServerErrorsAsCircuitBreakerFailures() {
        // Given
        WebClientResponseException serverError = WebClientResponseException.create(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                null, null, null
        );

        // When & Then
        assertThat(ErrorClassification.shouldRecordFailure(serverError)).isTrue();
    }

    @Test
    void shouldNotRecordClientErrorsAsCircuitBreakerFailures() {
        // Given
        WebClientResponseException badRequest = WebClientResponseException.create(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                null, null, null
        );

        // When & Then
        assertThat(ErrorClassification.shouldRecordFailure(badRequest)).isFalse();
    }

    @Test
    void shouldRecord429AsCircuitBreakerFailure() {
        // Given - 429 indicates service degradation, should be monitored
        WebClientResponseException tooManyRequests = WebClientResponseException.create(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Too Many Requests",
                null, null, null
        );

        // When & Then
        assertThat(ErrorClassification.shouldRecordFailure(tooManyRequests)).isTrue();
    }

    @Test
    void shouldClassifyErrorTypes() {
        // Given
        WebClientResponseException serverError = WebClientResponseException.create(500, "Error", null, null, null);
        WebClientResponseException clientError = WebClientResponseException.create(400, "Error", null, null, null);
        WebClientResponseException rateLimitError = WebClientResponseException.create(429, "Error", null, null, null);
        TimeoutException timeoutException = new TimeoutException("Timeout");
        IOException ioException = new IOException("Network error");

        // When & Then
        assertThat(ErrorClassification.classifyError(serverError)).isEqualTo("SERVER_ERROR");
        assertThat(ErrorClassification.classifyError(clientError)).isEqualTo("CLIENT_ERROR");
        assertThat(ErrorClassification.classifyError(rateLimitError)).isEqualTo("RATE_LIMIT");
        assertThat(ErrorClassification.classifyError(timeoutException)).isEqualTo("TIMEOUT");
        assertThat(ErrorClassification.classifyError(ioException)).isEqualTo("NETWORK");
    }

    @Test
    void shouldHandleWrappedExceptions() {
        // Given
        IOException cause = new IOException("Connection refused");
        RuntimeException wrappedException = new RuntimeException("Wrapped", cause);

        // When & Then
        assertThat(ErrorClassification.isRetryable(wrappedException)).isTrue();
    }
}
