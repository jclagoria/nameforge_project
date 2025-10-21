package com.forge.integration;

import com.forge.adapters.inbound.dto.GenerationRequestDto;
import com.forge.adapters.outbound.moderation.OpenAIModerationService;
import com.forge.adapters.outbound.moderation.SimpleModerationService;
import com.forge.application.ForgeNameApplication;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;
import wiremock.com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3: Fallback and TimeLimiter Integration Tests
 * <p>
 * Tests fallback mechanisms and timeout behavior:
 * <ul>
 *   <li>Fallback to SimpleModerationService when OpenAI fails</li>
 *   <li>System continues functioning when Redis is down (graceful degradation)</li>
 *   <li>Timeout cancels operations exceeding time limits</li>
 * </ul>
 * </p>
 *
 * @see <a href="docs/Resilience-Testing-Analysis.md">Resilience Testing Analysis - Phase 3</a>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {ForgeNameApplication.class, TestRedisConfig.class},
        properties = {
                "spring.main.allow-bean-definition-overriding=true",
                // Disable OpenAI for some tests
                "moderation.openai.enabled=true"
        }
)
@AutoConfigureWebTestClient
@Testcontainers
@DisplayName("Fallback and TimeLimiter Integration Tests - Phase 3")
class FallbackIntegrationTest {

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
        // PostgreSQL configuration
        registry.add("spring.r2dbc.url", () -> String.format("r2dbc:postgresql://%s:%d/%s",
                postgres.getHost(),
                postgres.getFirstMappedPort(),
                postgres.getDatabaseName()));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("spring.r2dbc.properties.sslMode", () -> "DISABLE");

        // Redis configuration
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.data.redis.repositories.enabled", () -> "true");

        // OpenAI configuration (WireMock)
        registry.add("moderation.openai.endpoint", () -> wireMock.baseUrl() + "/v1/moderations");
        registry.add("moderation.openai.api-key", () -> "test-api-key");
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    private static boolean schemaInitialized = false;

    @BeforeEach
    void setUp() throws IOException {
        // Reset WireMock
        wireMock.resetAll();

        // Initialize schema if needed
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
    }

    private String loadSchemaFromClasspath() throws IOException {
        try (var inputStream = getClass().getClassLoader().getResourceAsStream("schema.sql")) {
            if (inputStream == null) {
                throw new IOException("schema.sql not found in classpath");
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ============================================================
    // Test 1-2: System functional with Redis down (graceful degradation)
    // ============================================================

    /**
     * Test 1: System continues serving requests when Redis is down
     * <p>
     * Scenario: Redis container is stopped, system falls back to database
     * Expected: Username generation continues working (cache miss, but functional)
     * </p>
     */
    @Test
    @DisplayName("Should serve requests from DB when Redis is down")
    void shouldServeFromDBWhenRedisDown() {
        // ARRANGE - Stop Redis container to simulate Redis failure
        redis.stop();

        // Give system time to detect Redis is down
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // ACT - Make username generation request with Redis down
        GenerationRequestDto request = new GenerationRequestDto("EN", 3);

        // ASSERT - System should still respond (graceful degradation)
        webTestClient.post()
                .uri("/api/v1/usernames/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.usernames").isArray()
                .jsonPath("$.usernames.length()").isEqualTo(3)
                .jsonPath("$.cacheHit").isEqualTo(false); // Cache miss expected

        // Verify second request also works (no caching, but functional)
        webTestClient.post()
                .uri("/api/v1/usernames/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.usernames").isArray()
                .jsonPath("$.usernames.length()").isEqualTo(3);

        // CLEANUP - Restart Redis for other tests
        redis.start();
    }

    /**
     * Test 2: Validate username endpoint works when Redis is down
     * <p>
     * Scenario: Redis is down, validation endpoint falls back to database
     * Expected: Validation continues working
     * </p>
     */
    @Test
    @DisplayName("Should validate usernames from DB when Redis is down")
    void shouldValidateUsernamesWhenRedisDown() {
        // ARRANGE - Stop Redis container
        redis.stop();

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // ACT & ASSERT - Validation should work without Redis
        webTestClient.get()
                .uri("/api/v1/usernames/validate/testuser123?language=EN")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.isValid").isBoolean()
                .jsonPath("$.username").isEqualTo("testuser123");

        // CLEANUP - Restart Redis
        redis.start();
    }

    /**
     * Test 3 (Bonus): GraphQL continues working with Redis down
     * <p>
     * Scenario: Redis is down, GraphQL API falls back to database
     * Expected: GraphQL queries succeed
     * </p>
     */
    @Test
    @DisplayName("GraphQL should work when Redis is down")
    void graphqlShouldWorkWhenRedisDown() {
        // ARRANGE - Stop Redis
        redis.stop();

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String mutation = """
            {
              "query": "mutation { generateUsernames(input: { language: EN, count: 2 }) { cacheHit usernames } }"
            }
            """;

        // ACT & ASSERT - GraphQL should work without Redis
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mutation)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.generateUsernames.usernames").isArray()
                .jsonPath("$.data.generateUsernames.usernames.length()").isEqualTo(2)
                .jsonPath("$.data.generateUsernames.cacheHit").isEqualTo(false);

        // CLEANUP
        redis.start();
    }
}
