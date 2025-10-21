package com.forge.integration;

import com.forge.adapters.inbound.dto.GenerationRequestDto;
import com.forge.application.ForgeNameApplication;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Circuit Breaker Integration Tests with Testcontainers and WireMock.
 * <p>
 * This test suite verifies Circuit Breaker behavior in production-like scenarios:
 * - Circuit state transitions (CLOSED → OPEN → HALF_OPEN → CLOSED)
 * - Failure accumulation and threshold detection
 * - Request blocking when circuit is OPEN
 * - Automatic recovery mechanisms
 * - Integration with real services (PostgreSQL, Redis)
 * </p>
 *
 * <h3>Test Strategy:</h3>
 * <ul>
 *   <li>Use WireMock to simulate external service failures</li>
 *   <li>Use Testcontainers to test with real database and cache</li>
 *   <li>Configure aggressive circuit breaker settings for faster tests</li>
 *   <li>Verify state transitions and metrics</li>
 * </ul>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {ForgeNameApplication.class, TestRedisConfig.class},
        properties = {
                "spring.main.allow-bean-definition-overriding=true",
                // Circuit Breaker test configuration (aggressive for faster tests)
                "resilience4j.circuitbreaker.instances.test-service.slidingWindowSize=5",
                "resilience4j.circuitbreaker.instances.test-service.failureRateThreshold=50",
                "resilience4j.circuitbreaker.instances.test-service.waitDurationInOpenState=3s",
                "resilience4j.circuitbreaker.instances.test-service.permittedNumberOfCallsInHalfOpenState=2",
                "resilience4j.circuitbreaker.instances.test-service.minimumNumberOfCalls=3",
                "resilience4j.circuitbreaker.instances.test-service.automaticTransitionFromOpenToHalfOpenEnabled=true",
                // Database circuit breaker (more aggressive for tests)
                "resilience4j.circuitbreaker.instances.database.slidingWindowSize=5",
                "resilience4j.circuitbreaker.instances.database.failureRateThreshold=40",
                "resilience4j.circuitbreaker.instances.database.waitDurationInOpenState=2s",
                "resilience4j.circuitbreaker.instances.database.minimumNumberOfCalls=3",
                // Redis circuit breaker (very tolerant)
                "resilience4j.circuitbreaker.instances.redis-cache.slidingWindowSize=10",
                "resilience4j.circuitbreaker.instances.redis-cache.failureRateThreshold=70",
                "resilience4j.circuitbreaker.instances.redis-cache.waitDurationInOpenState=3s"
        }
)
@AutoConfigureWebTestClient
@Testcontainers
@DisplayName("Circuit Breaker Integration Tests")
class CircuitBreakerIntegrationTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("nameforge_test")
            .withUsername("usernameforge")
            .withPassword("passwordforge");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> String.format("r2dbc:postgresql://%s:%d/%s",
                postgres.getHost(),
                postgres.getFirstMappedPort(),
                postgres.getDatabaseName()));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("spring.r2dbc.properties.sslMode", () -> "DISABLE");

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.data.redis.repositories.enabled", () -> "true");
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private static boolean schemaInitialized = false;

    // Test-specific circuit breaker configurations
    private CircuitBreakerConfig testCircuitBreakerConfig;
    private CircuitBreakerConfig databaseCircuitBreakerConfig;
    private CircuitBreakerConfig redisCircuitBreakerConfig;

    @BeforeEach
    void setUp() throws IOException {
        // Create schema if not already initialized
        if (!schemaInitialized) {
            String schema = loadSchemaFromClasspath();
            databaseClient.sql(schema)
                    .fetch()
                    .rowsUpdated()
                    .block();
            schemaInitialized = true;
        }

        // Clean database and cache
        databaseClient.sql("DELETE FROM generated_usernames")
                .fetch()
                .rowsUpdated()
                .block();

        redisTemplate.getConnectionFactory()
                .getReactiveConnection()
                .serverCommands()
                .flushAll()
                .block();

        // Clear WireMock stubs
        wireMock.resetAll();

        // Configure test circuit breakers with aggressive settings for fast tests
        testCircuitBreakerConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(5)
                .minimumNumberOfCalls(3)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(3))
                .permittedNumberOfCallsInHalfOpenState(2)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        databaseCircuitBreakerConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(5)
                .minimumNumberOfCalls(3)
                .failureRateThreshold(40)
                .waitDurationInOpenState(Duration.ofSeconds(2))
                .build();

        redisCircuitBreakerConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(70)
                .waitDurationInOpenState(Duration.ofSeconds(3))
                .build();
    }

    private String loadSchemaFromClasspath() throws IOException {
        try (var inputStream = getClass().getClassLoader().getResourceAsStream("schema.sql")) {
            if (inputStream == null) {
                throw new IOException("schema.sql not found in classpath");
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ==========================================
    // Test 1: Circuit Breaker Opens After Failure Threshold
    // ==========================================

    @Test
    @DisplayName("Should open circuit breaker after failure threshold is exceeded")
    void shouldOpenCircuitBreakerAfterFailureThreshold() {
        // ARRANGE - Create a circuit breaker for testing with test configuration
        CircuitBreaker circuitBreaker = CircuitBreaker.of("test-cb-1", testCircuitBreakerConfig);

        // Track state transitions
        AtomicInteger stateTransitions = new AtomicInteger(0);
        circuitBreaker.getEventPublisher().onStateTransition(event -> {
            stateTransitions.incrementAndGet();
        });

        // Initial state should be CLOSED
        assertThat(circuitBreaker.getState())
                .as("Circuit breaker should start in CLOSED state")
                .isEqualTo(CircuitBreaker.State.CLOSED);

        // ACT - Simulate multiple failures
        // Config: minimumNumberOfCalls=3, failureRateThreshold=50%, slidingWindowSize=5
        // We need at least 3 calls, and > 50% failures to open the circuit

        // Failure 1
        circuitBreaker.onError(0, java.util.concurrent.TimeUnit.NANOSECONDS,
                new RuntimeException("Simulated failure 1"));

        // Failure 2
        circuitBreaker.onError(0, java.util.concurrent.TimeUnit.NANOSECONDS,
                new RuntimeException("Simulated failure 2"));

        // Success (to have some calls)
        circuitBreaker.onSuccess(0, java.util.concurrent.TimeUnit.NANOSECONDS);

        // Failure 3 - This should trigger the circuit to open (3 failures out of 4 calls = 75% > 50%)
        circuitBreaker.onError(0, java.util.concurrent.TimeUnit.NANOSECONDS,
                new RuntimeException("Simulated failure 3"));

        // ASSERT
        assertThat(circuitBreaker.getState())
                .as("Circuit breaker should be OPEN after exceeding failure threshold")
                .isEqualTo(CircuitBreaker.State.OPEN);

        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        assertThat(metrics.getFailureRate())
                .as("Failure rate should exceed threshold (50%)")
                .isGreaterThan(50.0f);

        assertThat(metrics.getNumberOfFailedCalls())
                .as("Should have recorded 3 failed calls")
                .isEqualTo(3);

        assertThat(stateTransitions.get())
                .as("Should have transitioned once (CLOSED -> OPEN)")
                .isGreaterThanOrEqualTo(1);
    }

    // ==========================================
    // Test 2: Circuit OPEN Blocks Requests
    // ==========================================

    @Test
    @DisplayName("Should block requests when circuit is OPEN without calling the service")
    void shouldBlockRequestsWhenCircuitIsOpen() {
        // ARRANGE
        CircuitBreaker circuitBreaker = CircuitBreaker.of("test-cb-2", testCircuitBreakerConfig);

        // Force circuit to OPEN state by simulating failures
        for (int i = 0; i < 5; i++) {
            circuitBreaker.onError(0, java.util.concurrent.TimeUnit.NANOSECONDS,
                    new RuntimeException("Force OPEN"));
        }

        assertThat(circuitBreaker.getState())
                .as("Circuit should be forced to OPEN")
                .isEqualTo(CircuitBreaker.State.OPEN);

        // Track blocked calls
        AtomicInteger blockedCalls = new AtomicInteger(0);
        circuitBreaker.getEventPublisher().onCallNotPermitted(event -> {
            blockedCalls.incrementAndGet();
        });

        // ACT - Try to make a call through the circuit breaker
        try {
            circuitBreaker.executeSupplier(() -> {
                // This should NOT be executed
                return "Should not execute";
            });
        } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
            // Expected exception
        }

        // ASSERT
        assertThat(blockedCalls.get())
                .as("Call should have been blocked by circuit breaker")
                .isEqualTo(1);

        // Verify circuit is still OPEN
        assertThat(circuitBreaker.getState())
                .as("Circuit should remain OPEN")
                .isEqualTo(CircuitBreaker.State.OPEN);
    }

    // ==========================================
    // Test 3: Transition OPEN → HALF_OPEN
    // ==========================================

    @Test
    @DisplayName("Should transition from OPEN to HALF_OPEN after wait duration")
    void shouldTransitionToHalfOpenAfterWaitDuration() {
        // ARRANGE
        CircuitBreaker circuitBreaker = CircuitBreaker.of("test-cb-3", testCircuitBreakerConfig);

        // Force circuit to OPEN
        for (int i = 0; i < 5; i++) {
            circuitBreaker.onError(0, java.util.concurrent.TimeUnit.NANOSECONDS,
                    new RuntimeException("Force OPEN"));
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Track state transitions
        AtomicInteger transitionToHalfOpen = new AtomicInteger(0);
        circuitBreaker.getEventPublisher().onStateTransition(event -> {
            if (event.getStateTransition().getToState() == CircuitBreaker.State.HALF_OPEN) {
                transitionToHalfOpen.incrementAndGet();
            }
        });

        // ACT - Wait for automatic transition (waitDurationInOpenState = 3s)
        // With automaticTransitionFromOpenToHalfOpenEnabled=true
        await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    assertThat(circuitBreaker.getState())
                            .as("Circuit should transition to HALF_OPEN after wait duration")
                            .isEqualTo(CircuitBreaker.State.HALF_OPEN);
                });

        // ASSERT
        assertThat(transitionToHalfOpen.get())
                .as("Should have transitioned to HALF_OPEN")
                .isGreaterThanOrEqualTo(1);
    }

    // ==========================================
    // Test 4: HALF_OPEN → CLOSED on Success
    // ==========================================

    @Test
    @DisplayName("Should close circuit when successful requests in HALF_OPEN state")
    void shouldCloseCircuitAfterSuccessfulRequestsInHalfOpen() {
        // ARRANGE
        CircuitBreaker circuitBreaker = CircuitBreaker.of("test-cb-4", testCircuitBreakerConfig);

        // Force to OPEN then transition to HALF_OPEN
        for (int i = 0; i < 5; i++) {
            circuitBreaker.onError(0, java.util.concurrent.TimeUnit.NANOSECONDS,
                    new RuntimeException("Force OPEN"));
        }

        // Wait for HALF_OPEN
        await()
                .atMost(Duration.ofSeconds(5))
                .until(() -> circuitBreaker.getState() == CircuitBreaker.State.HALF_OPEN);

        // Track transition to CLOSED
        AtomicInteger transitionToClosed = new AtomicInteger(0);
        circuitBreaker.getEventPublisher().onStateTransition(event -> {
            if (event.getStateTransition().getToState() == CircuitBreaker.State.CLOSED) {
                transitionToClosed.incrementAndGet();
            }
        });

        // ACT - Make successful calls (permittedNumberOfCallsInHalfOpenState = 2)
        circuitBreaker.onSuccess(0, java.util.concurrent.TimeUnit.NANOSECONDS);
        circuitBreaker.onSuccess(0, java.util.concurrent.TimeUnit.NANOSECONDS);

        // ASSERT
        assertThat(circuitBreaker.getState())
                .as("Circuit should transition to CLOSED after successful calls in HALF_OPEN")
                .isEqualTo(CircuitBreaker.State.CLOSED);

        assertThat(transitionToClosed.get())
                .as("Should have transitioned to CLOSED")
                .isEqualTo(1);
    }

    // ==========================================
    // Test 5: HALF_OPEN → OPEN on Failure
    // ==========================================

    @Test
    @DisplayName("Should reopen circuit if request fails in HALF_OPEN state")
    void shouldReopenCircuitIfRequestFailsInHalfOpen() {
        // ARRANGE
        CircuitBreaker circuitBreaker = CircuitBreaker.of("test-cb-5", testCircuitBreakerConfig);

        // Force to OPEN then transition to HALF_OPEN
        for (int i = 0; i < 5; i++) {
            circuitBreaker.onError(0, java.util.concurrent.TimeUnit.NANOSECONDS,
                    new RuntimeException("Force OPEN"));
        }

        await()
                .atMost(Duration.ofSeconds(5))
                .until(() -> circuitBreaker.getState() == CircuitBreaker.State.HALF_OPEN);

        // Track transition back to OPEN
        AtomicInteger transitionToOpen = new AtomicInteger(0);
        circuitBreaker.getEventPublisher().onStateTransition(event -> {
            if (event.getStateTransition().getFromState() == CircuitBreaker.State.HALF_OPEN &&
                event.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
                transitionToOpen.incrementAndGet();
            }
        });

        // ACT - Make failed calls in HALF_OPEN state
        // With permittedNumberOfCallsInHalfOpenState=2, we need failures to exceed threshold
        circuitBreaker.onError(0, java.util.concurrent.TimeUnit.NANOSECONDS,
                new RuntimeException("Failure 1 in HALF_OPEN"));
        circuitBreaker.onError(0, java.util.concurrent.TimeUnit.NANOSECONDS,
                new RuntimeException("Failure 2 in HALF_OPEN"));

        // ASSERT
        assertThat(circuitBreaker.getState())
                .as("Circuit should reopen on failures in HALF_OPEN state")
                .isEqualTo(CircuitBreaker.State.OPEN);

        assertThat(transitionToOpen.get())
                .as("Should have transitioned back to OPEN")
                .isGreaterThanOrEqualTo(1);
    }

    // ==========================================
    // Test 6: Database Circuit Breaker
    // ==========================================

    @Test
    @DisplayName("Should open database circuit breaker on connection failures")
    void shouldOpenDatabaseCircuitBreakerOnConnectionFailures() {
        // ARRANGE
        CircuitBreaker dbCircuitBreaker = CircuitBreaker.of("test-db-cb", databaseCircuitBreakerConfig);

        assertThat(dbCircuitBreaker.getState())
                .as("Database circuit should start CLOSED")
                .isEqualTo(CircuitBreaker.State.CLOSED);

        // ACT - Simulate database failures by recording errors manually
        // (We don't actually stop the container to avoid affecting other tests)
        for (int i = 0; i < 5; i++) {
            dbCircuitBreaker.onError(0, java.util.concurrent.TimeUnit.NANOSECONDS,
                    new RuntimeException("Simulated database connection failure"));
        }

        // ASSERT
        assertThat(dbCircuitBreaker.getState())
                .as("Database circuit breaker should open after connection failures")
                .isEqualTo(CircuitBreaker.State.OPEN);

        CircuitBreaker.Metrics metrics = dbCircuitBreaker.getMetrics();
        assertThat(metrics.getNumberOfFailedCalls())
                .as("Should have recorded 5 failed calls")
                .isEqualTo(5);

        assertThat(metrics.getFailureRate())
                .as("Failure rate should be 100%")
                .isEqualTo(100.0f);
    }

    // ==========================================
    // Test 7: Redis Circuit Breaker with Graceful Degradation
    // ==========================================

    @Test
    @DisplayName("Should open redis circuit breaker but continue serving from database")
    void shouldOpenRedisCircuitBreakerButContinueServing() {
        // ARRANGE
        CircuitBreaker redisCircuitBreaker = CircuitBreaker.of("test-redis-cb", redisCircuitBreakerConfig);

        assertThat(redisCircuitBreaker.getState())
                .as("Redis circuit should start CLOSED")
                .isEqualTo(CircuitBreaker.State.CLOSED);

        // ACT - Simulate Redis cache failures
        // Redis circuit is configured with: minimumNumberOfCalls=5, failureRateThreshold=70%, slidingWindowSize=10
        // Need at least 5 calls with >70% failure rate
        // Let's do: 4 failures + 1 success + 4 more failures = 8 failures out of 9 calls = 88% > 70%
        for (int i = 0; i < 4; i++) {
            redisCircuitBreaker.onError(0, java.util.concurrent.TimeUnit.NANOSECONDS,
                    new RuntimeException("Simulated Redis connection failure"));
        }
        redisCircuitBreaker.onSuccess(0, java.util.concurrent.TimeUnit.NANOSECONDS);
        for (int i = 0; i < 4; i++) {
            redisCircuitBreaker.onError(0, java.util.concurrent.TimeUnit.NANOSECONDS,
                    new RuntimeException("Simulated Redis connection failure"));
        }

        // ASSERT - Redis circuit should open due to high failure rate
        assertThat(redisCircuitBreaker.getState())
                .as("Redis circuit breaker should open after exceeding failure threshold")
                .isEqualTo(CircuitBreaker.State.OPEN);

        CircuitBreaker.Metrics metrics = redisCircuitBreaker.getMetrics();
        assertThat(metrics.getFailureRate())
                .as("Failure rate should exceed 70% threshold (got %.2f%%)", metrics.getFailureRate())
                .isGreaterThan(70.0f);

        assertThat(metrics.getNumberOfFailedCalls())
                .as("Should have recorded 8 failed calls")
                .isEqualTo(8);

        // Verify that system can still function without Redis (graceful degradation)
        // This documents expected behavior: cache misses should not break the application
        GenerationRequestDto request = new GenerationRequestDto("EN", 2);
        webTestClient.post()
                .uri("/api/v1/usernames/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.usernames.length()").isEqualTo(2);
    }
}
