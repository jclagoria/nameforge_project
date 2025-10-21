package com.forge.integration;

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
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GraphQL Integration Tests using WebTestClient and Testcontainers.
 * Tests the complete GraphQL stack with real PostgreSQL and Redis.
 *
 * This test suite verifies:
 * - GraphQL mutation execution via HTTP
 * - Schema validation and query parsing
 * - Cache behavior (hit/miss) through GraphQL
 * - Performance metrics
 * - Variable handling
 * - Error responses
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {ForgeNameApplication.class, TestRedisConfig.class},
        properties = {
                "spring.main.allow-bean-definition-overriding=true"
        }
)
@AutoConfigureWebTestClient
@Testcontainers
@DisplayName("GraphQL Integration Tests")
class GraphQLIntegrationTest {

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
    @DisplayName("Should execute GraphQL mutation and generate usernames successfully")
    void shouldExecuteGraphQLMutationSuccessfully() {
        // ARRANGE
        String mutation = """
            {
              "query": "mutation { generateUsernames(input: { language: EN, count: 3 }) { usernames generatedAt language totalGenerated cacheHit responseTimeMs } }"
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
                .jsonPath("$.data.generateUsernames.usernames").isArray()
                .jsonPath("$.data.generateUsernames.usernames.length()").isEqualTo(3)
                .jsonPath("$.data.generateUsernames.language").isEqualTo("EN")
                .jsonPath("$.data.generateUsernames.totalGenerated").isEqualTo(3)
                .jsonPath("$.data.generateUsernames.cacheHit").isEqualTo(false)
                .jsonPath("$.data.generateUsernames.responseTimeMs").isNumber();
    }

    @Test
    @DisplayName("Should return cache hit on subsequent GraphQL request with same parameters")
    void shouldReturnCacheHitOnSubsequentGraphQLRequest() {
        // ARRANGE
        String mutation = """
            {
              "query": "mutation { generateUsernames(input: { language: EN, count: 2 }) { usernames cacheHit } }"
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
    @DisplayName("Should execute GraphQL mutation with variables")
    void shouldExecuteGraphQLMutationWithVariables() {
        // ARRANGE
        Map<String, Object> request = Map.of(
                "query", "mutation GenerateUsernames($input: GenerateUsernamesInput!) { generateUsernames(input: $input) { usernames language totalGenerated } }",
                "variables", Map.of(
                        "input", Map.of(
                                "language", "ES",
                                "count", 5
                        )
                )
        );

        // ACT & ASSERT
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.generateUsernames.usernames").isArray()
                .jsonPath("$.data.generateUsernames.usernames.length()").isEqualTo(5)
                .jsonPath("$.data.generateUsernames.language").isEqualTo("ES")
                .jsonPath("$.data.generateUsernames.totalGenerated").isEqualTo(5);
    }

    @Test
    @DisplayName("Should generate single username when count is not provided (default)")
    void shouldGenerateSingleUsernameWithDefaultCount() {
        // ARRANGE
        String mutation = """
            {
              "query": "mutation { generateUsernames(input: { language: EN }) { usernames totalGenerated } }"
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
                .jsonPath("$.data.generateUsernames.usernames").isArray()
                .jsonPath("$.data.generateUsernames.usernames.length()").isEqualTo(1)
                .jsonPath("$.data.generateUsernames.totalGenerated").isEqualTo(1);
    }

    @Test
    @DisplayName("Should demonstrate cache performance improvement in GraphQL")
    void shouldDemonstrateCachePerformanceImprovementInGraphQL() {
        // ARRANGE
        String mutation = """
            {
              "query": "mutation { generateUsernames(input: { language: EN, count: 5 }) { cacheHit responseTimeMs } }"
            }
            """;

        AtomicReference<Long> cacheMissTime = new AtomicReference<>();
        AtomicReference<Long> cacheHitTime = new AtomicReference<>();

        // First request - cache miss
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

        // Second request - cache hit
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

        // ASSERT - Cache hit should be significantly faster (at least 50% improvement)
        long maxAcceptableCacheHitTime = (long) (cacheMissTime.get() * 0.5);
        assertThat(cacheHitTime.get())
                .as("Cache hit should be at least 50%% faster than cache miss (miss: %dms, hit: %dms)",
                        cacheMissTime.get(), cacheHitTime.get())
                .isLessThan(maxAcceptableCacheHitTime);
    }

    @Test
    @DisplayName("Should complete GraphQL mutation within time limit")
    void shouldCompleteGraphQLMutationWithinTimeLimit() {
        // ARRANGE
        String mutation = """
            {
              "query": "mutation { generateUsernames(input: { language: EN, count: 5 }) { responseTimeMs cacheHit } }"
            }
            """;

        AtomicReference<Long> responseTime = new AtomicReference<>();

        // ACT
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mutation)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.generateUsernames.cacheHit").isEqualTo(false)
                .jsonPath("$.data.generateUsernames.responseTimeMs").value(time ->
                        responseTime.set(((Number) time).longValue())
                );

        // ASSERT - Integration test should complete within 500ms
        assertThat(responseTime.get())
                .as("GraphQL generation should complete within 500ms")
                .isLessThan(500L);
    }

    @Test
    @DisplayName("Should handle GraphQL validation errors gracefully")
    void shouldHandleGraphQLValidationErrorsGracefully() {
        // ARRANGE - Count exceeds maximum
        String mutation = """
            {
              "query": "mutation { generateUsernames(input: { language: EN, count: 15 }) { usernames } }"
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
                .jsonPath("$.errors").isArray()
                .jsonPath("$.errors").isNotEmpty()
                .jsonPath("$.errors[0].message").exists();
    }

    @Test
    @DisplayName("Should handle invalid language in GraphQL mutation")
    void shouldHandleInvalidLanguageInGraphQLMutation() {
        // ARRANGE - Invalid language code
        String mutation = """
            {
              "query": "mutation { generateUsernames(input: { language: FR, count: 3 }) { usernames } }"
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
                .jsonPath("$.errors").isArray()
                .jsonPath("$.errors[0].message").exists();
    }

    @Test
    @DisplayName("Should execute health check GraphQL query")
    void shouldExecuteHealthCheckGraphQLQuery() {
        // ARRANGE
        String query = """
            {
              "query": "query { _health }"
            }
            """;

        // ACT & ASSERT
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(query)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data._health").isEqualTo(true);
    }

    @Test
    @DisplayName("Should generate maximum allowed usernames via GraphQL")
    void shouldGenerateMaximumAllowedUsernamesViaGraphQL() {
        // ARRANGE
        String mutation = """
            {
              "query": "mutation { generateUsernames(input: { language: EN, count: 10 }) { usernames totalGenerated } }"
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
                .jsonPath("$.data.generateUsernames.usernames").isArray()
                .jsonPath("$.data.generateUsernames.usernames.length()").isEqualTo(10)
                .jsonPath("$.data.generateUsernames.totalGenerated").isEqualTo(10);
    }

    @Test
    @DisplayName("Should persist generated usernames to database via GraphQL")
    void shouldPersistGeneratedUsernamesToDatabaseViaGraphQL() {
        // ARRANGE
        String mutation = """
            {
              "query": "mutation { generateUsernames(input: { language: EN, count: 3 }) { usernames } }"
            }
            """;

        // ACT - Generate usernames via GraphQL
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mutation)
                .exchange()
                .expectStatus().isOk();

        // ASSERT - Verify usernames were persisted to database
        Long count = databaseClient.sql("SELECT COUNT(*) FROM generated_usernames")
                .map(row -> row.get(0, Long.class))
                .one()
                .block();

        assertThat(count)
                .as("Should have persisted 3 usernames to database")
                .isEqualTo(3L);
    }

}
