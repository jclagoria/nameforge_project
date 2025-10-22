package com.forge.integration;

import com.forge.adapters.inbound.dto.GenerationRequestDto;
import com.forge.application.ForgeNameApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {ForgeNameApplication.class, TestRedisConfig.class},
        properties = {
                "spring.main.allow-bean-definition-overriding=true",
                // Enable SimpleModerationService by disabling external APIs
                "moderation.openai.enabled=false",
                "moderation.perspective.enabled=false"
        }
)
@AutoConfigureWebTestClient
@Testcontainers
@DisplayName("Cache Integration Tests")
class CacheIntegrationTest {

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

    private static boolean schemaInitialized = false;

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
    }

    private String loadSchemaFromClasspath() throws IOException {
        try (var inputStream = getClass().getClassLoader().getResourceAsStream("schema.sql")) {
            if (inputStream == null) {
                throw new IOException("schema.sql not found in classpath");
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("Should return cache miss (cacheHit=false) on first username generation request")
    void shouldHaveCacheMissOnFirstRequest() {
        // ARRANGE
        GenerationRequestDto request = new GenerationRequestDto("EN", 3);

        // ACT & ASSERT
        webTestClient.post()
                .uri("/api/v1/usernames/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.cacheHit").isEqualTo(false)
                .jsonPath("$.usernames.length()").isEqualTo(3);
    }

    @Test
    @DisplayName("Should return cache hit (cacheHit=true) on subsequent request with same parameters")
    void shouldHaveCacheHitOnSubsequentRequest() {
        // ARRANGE
        GenerationRequestDto request = new GenerationRequestDto("EN", 2);

        // First request - populate cache
        webTestClient.post()
                .uri("/api/v1/usernames/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.cacheHit").isEqualTo(false);

        // Second request - should hit cache
        webTestClient.post()
                .uri("/api/v1/usernames/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.cacheHit").isEqualTo(true)
                .jsonPath("$.usernames.length()").isEqualTo(2);
    }

    @Test
    @DisplayName("Should complete username generation within 500ms time limit (integration test with Testcontainers)")
    void shouldCompleteGenerationWithinTimeLimit() {
        // ARRANGE
        GenerationRequestDto request = new GenerationRequestDto("EN", 5);
        AtomicReference<Long> responseTime = new AtomicReference<>();

        // ACT - First generation (cache miss)
        webTestClient.post()
                .uri("/api/v1/usernames/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.cacheHit").isEqualTo(false)
                .jsonPath("$.responseTimeMs").value(time ->
                        responseTime.set(((Number) time).longValue())
                );

        // ASSERT - Integration test should complete within 500ms
        // (Production SLA is P99 < 300ms, but integration tests have Testcontainers overhead)
        assertThat(responseTime.get())
                .as("Generation in integration test should complete within 500ms")
                .isLessThan(500L);
    }

    @Test
    @DisplayName("Should demonstrate at least 50% performance improvement when using cache (cache hit vs cache miss)")
    void shouldShowPerformanceImprovementWithCache() {
        // ARRANGE
        GenerationRequestDto request = new GenerationRequestDto("EN", 5);
        AtomicReference<Long> cacheMissTime = new AtomicReference<>();
        AtomicReference<Long> cacheHitTime = new AtomicReference<>();

        // ACT - First request (cache miss)
        webTestClient.post()
                .uri("/api/v1/usernames/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.cacheHit").isEqualTo(false)
                .jsonPath("$.responseTimeMs").value(time ->
                        cacheMissTime.set(((Number) time).longValue())
                );

        // ACT - Second request (cache hit)
        webTestClient.post()
                .uri("/api/v1/usernames/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.cacheHit").isEqualTo(true)
                .jsonPath("$.responseTimeMs").value(time ->
                        cacheHitTime.set(((Number) time).longValue())
                );

        // ASSERT - Cache hit should be significantly faster (at least 50% improvement)
        long maxAcceptableCacheHitTime = (long) (cacheMissTime.get() * 0.5);
        assertThat(cacheHitTime.get())
                .as("Cache hit should be at least 50%% faster than cache miss (miss: %dms, hit: %dms)",
                        cacheMissTime.get(), cacheHitTime.get())
                .isLessThan(maxAcceptableCacheHitTime);
    }

    // ==========================================
    // GraphQL Cache Tests
    // ==========================================

    @Test
    @DisplayName("GraphQL: Should return cache miss on first request")
    void graphqlShouldHaveCacheMissOnFirstRequest() {
        // ARRANGE
        String mutation = """
            {
              "query": "mutation { generateUsernames(input: { language: EN, count: 3 }) { cacheHit usernames } }"
            }
            """;

        // ACT & ASSERT
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mutation)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.generateUsernames.cacheHit").isEqualTo(false)
                .jsonPath("$.data.generateUsernames.usernames.length()").isEqualTo(3);
    }

    @Test
    @DisplayName("GraphQL: Should return cache hit on subsequent request")
    void graphqlShouldHaveCacheHitOnSubsequentRequest() {
        // ARRANGE
        String mutation = """
            {
              "query": "mutation { generateUsernames(input: { language: ES, count: 2 }) { cacheHit usernames } }"
            }
            """;

        // First request - populate cache
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mutation)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.generateUsernames.cacheHit").isEqualTo(false);

        // Second request - should hit cache
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mutation)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.generateUsernames.cacheHit").isEqualTo(true)
                .jsonPath("$.data.generateUsernames.usernames.length()").isEqualTo(2);
    }

    @Test
    @DisplayName("GraphQL: Should demonstrate cache performance improvement")
    void graphqlShouldShowCachePerformanceImprovement() {
        // ARRANGE
        String mutation = """
            {
              "query": "mutation { generateUsernames(input: { language: EN, count: 5 }) { cacheHit responseTimeMs } }"
            }
            """;

        AtomicReference<Long> cacheMissTime = new AtomicReference<>();
        AtomicReference<Long> cacheHitTime = new AtomicReference<>();

        // First request (cache miss)
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mutation)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.generateUsernames.cacheHit").isEqualTo(false)
                .jsonPath("$.data.generateUsernames.responseTimeMs").value(time ->
                        cacheMissTime.set(((Number) time).longValue())
                );

        // Second request (cache hit)
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mutation)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.generateUsernames.cacheHit").isEqualTo(true)
                .jsonPath("$.data.generateUsernames.responseTimeMs").value(time ->
                        cacheHitTime.set(((Number) time).longValue())
                );

        // ASSERT - GraphQL cache hit should also be significantly faster
        long maxAcceptableCacheHitTime = (long) (cacheMissTime.get() * 0.5);
        assertThat(cacheHitTime.get())
                .as("GraphQL cache hit should be at least 50%% faster (miss: %dms, hit: %dms)",
                        cacheMissTime.get(), cacheHitTime.get())
                .isLessThan(maxAcceptableCacheHitTime);
    }

    @Test
    @DisplayName("REST and GraphQL should share the same cache")
    void restAndGraphQLShouldShareSameCache() {
        // ARRANGE
        GenerationRequestDto restRequest = new GenerationRequestDto("EN", 3);
        String graphqlMutation = """
            {
              "query": "mutation { generateUsernames(input: { language: EN, count: 3 }) { cacheHit usernames } }"
            }
            """;

        // ACT - First request via REST (populate cache)
        webTestClient.post()
                .uri("/api/v1/usernames/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(restRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.cacheHit").isEqualTo(false);

        // Second request via GraphQL (should hit cache)
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(graphqlMutation)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.generateUsernames.cacheHit").isEqualTo(true)
                .jsonPath("$.data.generateUsernames.usernames.length()").isEqualTo(3);
    }
}
