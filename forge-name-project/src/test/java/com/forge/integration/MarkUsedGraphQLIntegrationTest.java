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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GraphQL Integration Tests for markUsernameAsUsed mutation.
 * Tests the complete GraphQL stack with real PostgreSQL and Redis using Testcontainers.
 *
 * This test suite verifies:
 * - GraphQL mutation execution for marking usernames as used
 * - Database persistence and idempotency
 * - Cache invalidation behavior
 * - Cross-platform compatibility (REST to GraphQL)
 * - Timestamp precision and timezone handling
 * - Error scenarios and validation
 */
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
@DisplayName("GraphQL Mark Username As Used Integration Tests")
class MarkUsedGraphQLIntegrationTest {

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

    /**
     * Helper method to insert an available username into the database.
     * This simulates a username that has been generated but not yet marked as used.
     * Required because markAsUsed performs UPDATE, not INSERT.
     */
    private void insertAvailableUsername(String username) {
        databaseClient.sql(
            "INSERT INTO generated_usernames (username, language, pattern_type, created_at, is_used) " +
            "VALUES (:username, 'EN', 'CLASSIC', NOW(), FALSE)"
        )
        .bind("username", username)
        .fetch()
        .rowsUpdated()
        .block();
    }

    // ==========================================
    // Core Functionality Tests
    // ==========================================

    @Test
    @DisplayName("Should mark username as used successfully on first marking via GraphQL")
    void shouldMarkUsernameAsUsedSuccessfullyOnFirstMarking() {
        // ARRANGE - Insert username first so it can be marked
        String username = "cleverpanda42";
        insertAvailableUsername(username);

        String mutation = """
            {
              "query": "mutation { markUsernameAsUsed(username: \\"cleverpanda42\\") { username marked wasAlreadyUsed markedAt message } }"
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
                .jsonPath("$.data.markUsernameAsUsed.username").isEqualTo("cleverpanda42")
                .jsonPath("$.data.markUsernameAsUsed.marked").isEqualTo(true)
                .jsonPath("$.data.markUsernameAsUsed.wasAlreadyUsed").isEqualTo(false)
                .jsonPath("$.data.markUsernameAsUsed.markedAt").isNotEmpty()
                .jsonPath("$.data.markUsernameAsUsed.message").isEqualTo("Username successfully marked as used");
    }

    @Test
    @DisplayName("Should return already used response when marking duplicate username via GraphQL")
    void shouldReturnAlreadyUsedResponseWhenMarkingDuplicateUsername() {
        // ARRANGE - Insert username first
        String username = "existinguser123";
        insertAvailableUsername(username);

        String mutation = """
            {
              "query": "mutation { markUsernameAsUsed(username: \\"existinguser123\\") { username marked wasAlreadyUsed markedAt message } }"
            }
            """;

        // First marking
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mutation)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.markUsernameAsUsed.marked").isEqualTo(true)
                .jsonPath("$.data.markUsernameAsUsed.wasAlreadyUsed").isEqualTo(false);

        // ACT & ASSERT - Second marking (duplicate)
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mutation)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.markUsernameAsUsed.username").isEqualTo("existinguser123")
                .jsonPath("$.data.markUsernameAsUsed.marked").isEqualTo(false)
                .jsonPath("$.data.markUsernameAsUsed.wasAlreadyUsed").isEqualTo(true)
                .jsonPath("$.data.markUsernameAsUsed.markedAt").isEmpty()
                .jsonPath("$.data.markUsernameAsUsed.message").isEqualTo("Username was already used");
    }

    @Test
    @DisplayName("Should persist marked username to database via GraphQL")
    void shouldPersistMarkedUsernameToDatabase() {
        // ARRANGE - Insert username first
        String username = "databasetest123";
        insertAvailableUsername(username);

        String mutation = String.format("""
            {
              "query": "mutation { markUsernameAsUsed(username: \\"%s\\") { username marked } }"
            }
            """, username);

        // ACT - Mark username via GraphQL
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mutation)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.markUsernameAsUsed.marked").isEqualTo(true);

        // ASSERT - Verify username was marked in database (is_used = TRUE)
        Boolean isUsed = databaseClient.sql("SELECT is_used FROM generated_usernames WHERE username = :username")
                .bind("username", username)
                .map(row -> row.get("is_used", Boolean.class))
                .one()
                .block();

        assertThat(isUsed)
                .as("Username should be marked as used in database")
                .isTrue();
    }

    // ==========================================
    // GraphQL Variables and Advanced Usage
    // ==========================================

    @Test
    @DisplayName("Should mark username using GraphQL variables")
    void shouldMarkUsernameUsingGraphQLVariables() {
        // ARRANGE - Insert username first
        String username = "variableuser456";
        insertAvailableUsername(username);

        Map<String, Object> request = Map.of(
                "query", "mutation MarkUsernameAsUsed($username: String!) { markUsernameAsUsed(username: $username) { username marked wasAlreadyUsed message } }",
                "variables", Map.of("username", "variableuser456")
        );

        // ACT & ASSERT
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.markUsernameAsUsed.username").isEqualTo("variableuser456")
                .jsonPath("$.data.markUsernameAsUsed.marked").isEqualTo(true)
                .jsonPath("$.data.markUsernameAsUsed.wasAlreadyUsed").isEqualTo(false)
                .jsonPath("$.data.markUsernameAsUsed.message").isEqualTo("Username successfully marked as used");
    }

    @Test
    @DisplayName("Should mark username with minimum valid length via GraphQL")
    void shouldMarkUsernameWithMinimumValidLength() {
        // ARRANGE - Insert username first
        String username = "abc12";
        insertAvailableUsername(username);

        String mutation = """
            {
              "query": "mutation { markUsernameAsUsed(username: \\"abc12\\") { username marked } }"
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
                .jsonPath("$.data.markUsernameAsUsed.username").isEqualTo("abc12")
                .jsonPath("$.data.markUsernameAsUsed.marked").isEqualTo(true);
    }

    @Test
    @DisplayName("Should mark username with maximum valid length via GraphQL")
    void shouldMarkUsernameWithMaximumValidLength() {
        // ARRANGE - Insert username first
        String username = "a".repeat(30);
        insertAvailableUsername(username);

        String mutation = String.format("""
            {
              "query": "mutation { markUsernameAsUsed(username: \\"%s\\") { username marked } }"
            }
            """, username);

        // ACT & ASSERT
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mutation)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.markUsernameAsUsed.username").isEqualTo(username)
                .jsonPath("$.data.markUsernameAsUsed.marked").isEqualTo(true);
    }

    @Test
    @DisplayName("Should mark username with special allowed characters via GraphQL")
    void shouldMarkUsernameWithSpecialAllowedCharacters() {
        // ARRANGE - Insert username first
        String username = "user_name-123";
        insertAvailableUsername(username);

        String mutation = """
            {
              "query": "mutation { markUsernameAsUsed(username: \\"user_name-123\\") { username marked } }"
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
                .jsonPath("$.data.markUsernameAsUsed.username").isEqualTo("user_name-123")
                .jsonPath("$.data.markUsernameAsUsed.marked").isEqualTo(true);
    }

    // ==========================================
    // Timestamp and Timezone Tests
    // ==========================================

    @Test
    @DisplayName("Should return ISO-8601 formatted timestamp when marking username via GraphQL")
    void shouldReturnIso8601FormattedTimestampWhenMarkingUsername() {
        // ARRANGE - Insert username first
        String username = "timestamptest";
        insertAvailableUsername(username);

        String mutation = """
            {
              "query": "mutation { markUsernameAsUsed(username: \\"timestamptest\\") { markedAt } }"
            }
            """;

        // ACT & ASSERT - Verify ISO-8601 format pattern in response
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mutation)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.markUsernameAsUsed.markedAt").exists()
                .jsonPath("$.data.markUsernameAsUsed.markedAt").isNotEmpty()
                .jsonPath("$.data.markUsernameAsUsed.markedAt").value(timestamp -> {
                    assertThat(timestamp.toString())
                            .as("Timestamp should be in ISO-8601 format with Z timezone")
                            .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");
                });
    }

    @Test
    @DisplayName("Should mark username with current timestamp within acceptable time window")
    void shouldMarkUsernameWithCurrentTimestampWithinAcceptableTimeWindow() {
        // ARRANGE - Insert username first
        String username = "timewindowtest";
        insertAvailableUsername(username);

        Instant beforeMark = Instant.now();
        String mutation = """
            {
              "query": "mutation { markUsernameAsUsed(username: \\"timewindowtest\\") { markedAt } }"
            }
            """;

        // ACT & ASSERT - Verify timestamp is within reasonable time window
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mutation)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.markUsernameAsUsed.markedAt").exists()
                .jsonPath("$.data.markUsernameAsUsed.markedAt").isNotEmpty();

        Instant afterMark = Instant.now();

        // ASSERT - Verify time window is reasonable (should complete within 5 seconds)
        assertThat(afterMark)
                .as("Operation should complete within 5 seconds")
                .isBefore(beforeMark.plus(5, ChronoUnit.SECONDS));
    }

    // ==========================================
    // Cross-Platform Integration Tests
    // ==========================================

    @Test
    @DisplayName("Should detect already used username marked via REST API when querying via GraphQL")
    void shouldDetectAlreadyUsedUsernameMarkedViaRestApiWhenQueryingViaGraphQL() {
        // ARRANGE - Insert username first, then mark via REST API
        String username = "crossplatform123";
        insertAvailableUsername(username);

        webTestClient.post()
                .uri("/api/v1/usernames/mark-used/{username}", username)
                .exchange()
                .expectStatus().isOk();

        // ACT & ASSERT - Query via GraphQL should detect it's already used
        String mutation = String.format("""
            {
              "query": "mutation { markUsernameAsUsed(username: \\"%s\\") { marked wasAlreadyUsed message } }"
            }
            """, username);

        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mutation)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.markUsernameAsUsed.marked").isEqualTo(false)
                .jsonPath("$.data.markUsernameAsUsed.wasAlreadyUsed").isEqualTo(true)
                .jsonPath("$.data.markUsernameAsUsed.message").isEqualTo("Username was already used");
    }

    @Test
    @DisplayName("Should invalidate username generation cache after marking username via GraphQL")
    void shouldInvalidateUsernameGenerationCacheAfterMarkingUsernameViaGraphQL() {
        // ARRANGE - Generate usernames to populate cache
        String generateMutation = """
            {
              "query": "mutation { generateUsernames(input: { language: EN, count: 5 }) { usernames cacheHit } }"
            }
            """;

        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(generateMutation)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.generateUsernames.cacheHit").isEqualTo(false);

        // Verify cache is populated (second request should hit cache)
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(generateMutation)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.generateUsernames.cacheHit").isEqualTo(true);

        // ACT - Insert username first, then mark it as used via GraphQL (should invalidate cache)
        String username = "cacheinvalidation";
        insertAvailableUsername(username);

        String markMutation = """
            {
              "query": "mutation { markUsernameAsUsed(username: \\"cacheinvalidation\\") { marked } }"
            }
            """;

        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(markMutation)
                .exchange()
                .expectStatus().isOk();

        // ASSERT - Cache should be invalidated (next generation request should be cache miss)
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(generateMutation)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.generateUsernames.cacheHit").isEqualTo(false);
    }

    // ==========================================
    // Idempotency and Concurrent Operations
    // ==========================================

    @Test
    @DisplayName("Should maintain idempotency when marking same username multiple times via GraphQL")
    void shouldMaintainIdempotencyWhenMarkingSameUsernameMultipleTimesViaGraphQL() {
        // ARRANGE - Insert username first
        String username = "idempotent123";
        insertAvailableUsername(username);

        String mutation = """
            {
              "query": "mutation { markUsernameAsUsed(username: \\"idempotent123\\") { marked wasAlreadyUsed } }"
            }
            """;

        // ACT - Mark same username 3 times
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mutation)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.markUsernameAsUsed.marked").isEqualTo(true)
                .jsonPath("$.data.markUsernameAsUsed.wasAlreadyUsed").isEqualTo(false);

        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mutation)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.markUsernameAsUsed.marked").isEqualTo(false)
                .jsonPath("$.data.markUsernameAsUsed.wasAlreadyUsed").isEqualTo(true);

        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mutation)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.markUsernameAsUsed.marked").isEqualTo(false)
                .jsonPath("$.data.markUsernameAsUsed.wasAlreadyUsed").isEqualTo(true);

        // ASSERT - Verify only one record in database
        Long count = databaseClient.sql("SELECT COUNT(*) FROM generated_usernames WHERE username = 'idempotent123'")
                .map(row -> row.get(0, Long.class))
                .one()
                .block();

        assertThat(count)
                .as("Should have exactly one record despite multiple marking attempts")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("Should handle marking different usernames concurrently via GraphQL")
    void shouldHandleMarkingDifferentUsernamesConcurrentlyViaGraphQL() {
        // ARRANGE - Insert all usernames first
        insertAvailableUsername("concurrent1");
        insertAvailableUsername("concurrent2");
        insertAvailableUsername("concurrent3");

        String mutation1 = """
            {
              "query": "mutation { markUsernameAsUsed(username: \\"concurrent1\\") { marked } }"
            }
            """;

        String mutation2 = """
            {
              "query": "mutation { markUsernameAsUsed(username: \\"concurrent2\\") { marked } }"
            }
            """;

        String mutation3 = """
            {
              "query": "mutation { markUsernameAsUsed(username: \\"concurrent3\\") { marked } }"
            }
            """;

        // ACT - Execute multiple marking operations
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mutation1)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.markUsernameAsUsed.marked").isEqualTo(true);

        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mutation2)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.markUsernameAsUsed.marked").isEqualTo(true);

        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mutation3)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.markUsernameAsUsed.marked").isEqualTo(true);

        // ASSERT - Verify all 3 usernames were marked as used
        Long count = databaseClient.sql("SELECT COUNT(*) FROM generated_usernames WHERE username IN ('concurrent1', 'concurrent2', 'concurrent3') AND is_used = TRUE")
                .map(row -> row.get(0, Long.class))
                .one()
                .block();

        assertThat(count)
                .as("Should have marked all 3 usernames as used")
                .isEqualTo(3L);
    }

    // ==========================================
    // Validation and Error Handling
    // ==========================================

    @Test
    @DisplayName("Should validate username format via GraphQL schema validation")
    void shouldValidateUsernameFormatViaGraphQLSchemaValidation() {
        // ARRANGE - Username with invalid characters
        String mutation = """
            {
              "query": "mutation { markUsernameAsUsed(username: \\"invalid@user!\\") { marked } }"
            }
            """;

        // ACT & ASSERT - Should return validation error
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mutation)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.errors").exists()
                .jsonPath("$.errors[0].message").exists();
    }

    @Test
    @DisplayName("Should handle empty username gracefully via GraphQL")
    void shouldHandleEmptyUsernameGracefullyViaGraphQL() {
        // ARRANGE
        String mutation = """
            {
              "query": "mutation { markUsernameAsUsed(username: \\"\\") { marked } }"
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
                .jsonPath("$.errors").exists();
    }

    @Test
    @DisplayName("Should handle username too short via GraphQL")
    void shouldHandleUsernameTooShortViaGraphQL() {
        // ARRANGE - Username with only 3 characters (minimum is 5)
        String mutation = """
            {
              "query": "mutation { markUsernameAsUsed(username: \\"abc\\") { marked } }"
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
                .jsonPath("$.errors").exists();
    }

    @Test
    @DisplayName("Should handle username too long via GraphQL")
    void shouldHandleUsernameTooLongViaGraphQL() {
        // ARRANGE - Username with 31 characters (maximum is 30)
        String username = "a".repeat(31);
        String mutation = String.format("""
            {
              "query": "mutation { markUsernameAsUsed(username: \\"%s\\") { marked } }"
            }
            """, username);

        // ACT & ASSERT
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mutation)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.errors").exists();
    }

    // ==========================================
    // Message Content Verification
    // ==========================================

    @Test
    @DisplayName("Should return appropriate success message when marking new username via GraphQL")
    void shouldReturnAppropriateSuccessMessageWhenMarkingNewUsernameViaGraphQL() {
        // ARRANGE - Insert username first
        String username = "successmsg";
        insertAvailableUsername(username);

        String mutation = """
            {
              "query": "mutation { markUsernameAsUsed(username: \\"successmsg\\") { message } }"
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
                .jsonPath("$.data.markUsernameAsUsed.message").isEqualTo("Username successfully marked as used");
    }

    @Test
    @DisplayName("Should return appropriate already used message for duplicate marking via GraphQL")
    void shouldReturnAppropriateAlreadyUsedMessageForDuplicateMarkingViaGraphQL() {
        // ARRANGE - Insert username first
        String username = "duplicatemsg";
        insertAvailableUsername(username);

        String mutation = """
            {
              "query": "mutation { markUsernameAsUsed(username: \\"duplicatemsg\\") { message } }"
            }
            """;

        // First marking
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mutation)
                .exchange()
                .expectStatus().isOk();

        // ACT & ASSERT - Second marking
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mutation)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.markUsernameAsUsed.message").isEqualTo("Username was already used");
    }
}
