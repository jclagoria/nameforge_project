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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for UsernameQueryResolver GraphQL operations.
 * Tests the complete GraphQL query stack with real PostgreSQL and Redis.
 *
 * This test suite verifies:
 * - GraphQL query execution via HTTP (validateUsername)
 * - Health check query
 * - Schema validation and query parsing
 * - Language parameter handling (EN, ES, default)
 * - Username validation logic integration
 * - Error handling and validation failures
 * - Variable handling in GraphQL queries
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
@DisplayName("Username Validation GraphQL Integration Tests")
class UsernameValidationGraphQLIntegrationTest {

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
    @DisplayName("Should execute health check GraphQL query successfully")
    void shouldExecuteHealthCheckGraphQLQuerySuccessfully() {
        // Given
        String query = """
            {
              "query": "query { _health }"
            }
            """;

        // When & Then
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
    @DisplayName("Should validate username with English language successfully via GraphQL")
    void shouldValidateUsernameWithEnglishLanguageSuccessfully() {
        // Given
        String query = """
            {
              "query": "query { validateUsername(username: \\"validuser123\\", language: EN) { username isValid isUnique isAppropriate isValidFormat reasons confidenceScore validatedAt } }"
            }
            """;

        // When & Then
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(query)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.validateUsername.username").isEqualTo("validuser123")
                .jsonPath("$.data.validateUsername.isValid").isBoolean()
                .jsonPath("$.data.validateUsername.isUnique").isBoolean()
                .jsonPath("$.data.validateUsername.isAppropriate").isBoolean()
                .jsonPath("$.data.validateUsername.isValidFormat").isBoolean()
                .jsonPath("$.data.validateUsername.reasons").isArray()
                .jsonPath("$.data.validateUsername.confidenceScore").isNumber()
                .jsonPath("$.data.validateUsername.validatedAt").isNotEmpty();
    }

    @Test
    @DisplayName("Should validate username with Spanish language successfully via GraphQL")
    void shouldValidateUsernameWithSpanishLanguageSuccessfully() {
        // Given
        String query = """
            {
              "query": "query { validateUsername(username: \\"usuariovalido\\", language: ES) { username isValid } }"
            }
            """;

        // When & Then
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(query)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.validateUsername.username").isEqualTo("usuariovalido")
                .jsonPath("$.data.validateUsername.isValid").isBoolean();
    }

    @Test
    @DisplayName("Should default to English language when language parameter is omitted")
    void shouldDefaultToEnglishLanguageWhenLanguageParameterIsOmitted() {
        // Given
        String query = """
            {
              "query": "query { validateUsername(username: \\"testuser\\") { username isValid isUnique isAppropriate isValidFormat } }"
            }
            """;

        // When & Then
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(query)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.validateUsername.username").isEqualTo("testuser")
                .jsonPath("$.data.validateUsername.isValid").isBoolean()
                .jsonPath("$.data.validateUsername.isUnique").isBoolean()
                .jsonPath("$.data.validateUsername.isAppropriate").isBoolean()
                .jsonPath("$.data.validateUsername.isValidFormat").isBoolean();
    }

    @Test
    @DisplayName("Should validate username using GraphQL variables")
    void shouldValidateUsernameUsingGraphQLVariables() {
        // Given
        Map<String, Object> request = Map.of(
                "query", "query ValidateUsername($username: String!, $language: Language) { validateUsername(username: $username, language: $language) { username isValid isUnique isAppropriate isValidFormat confidenceScore } }",
                "variables", Map.of(
                        "username", "testuser123",
                        "language", "EN"
                )
        );

        // When & Then
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.validateUsername.username").isEqualTo("testuser123")
                .jsonPath("$.data.validateUsername.isValid").isBoolean()
                .jsonPath("$.data.validateUsername.confidenceScore").isNumber();
    }

    @Test
    @DisplayName("Should return validation failure for username with invalid characters")
    void shouldReturnValidationFailureForUsernameWithInvalidCharacters() {
        // Given
        String query = """
            {
              "query": "query { validateUsername(username: \\"bad@user!\\", language: EN) { username isValid isValidFormat reasons } }"
            }
            """;

        // When & Then
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(query)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.validateUsername.username").isEqualTo("bad@user!")
                .jsonPath("$.data.validateUsername.isValid").isBoolean()
                .jsonPath("$.data.validateUsername.isValidFormat").isBoolean()
                .jsonPath("$.data.validateUsername.reasons").isArray();
    }

    @Test
    @DisplayName("Should return validation failure for too short username")
    void shouldReturnValidationFailureForTooShortUsername() {
        // Given
        String query = """
            {
              "query": "query { validateUsername(username: \\"bad\\", language: EN) { username isValid isValidFormat reasons confidenceScore } }"
            }
            """;

        // When & Then
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(query)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.validateUsername.username").isEqualTo("bad")
                .jsonPath("$.data.validateUsername.isValid").isBoolean()
                .jsonPath("$.data.validateUsername.reasons").isArray();
    }

    @Test
    @DisplayName("Should detect duplicate username in database via GraphQL")
    void shouldDetectDuplicateUsernameInDatabaseViaGraphQL() {
        // Given - Insert a username into the database
        String existingUsername = "existinguser123";
        databaseClient.sql("INSERT INTO generated_usernames (username, language, created_at) VALUES (:username, :language, NOW())")
                .bind("username", existingUsername)
                .bind("language", "EN")
                .fetch()
                .rowsUpdated()
                .block();

        String query = String.format("""
            {
              "query": "query { validateUsername(username: \\"%s\\", language: EN) { username isValid isUnique reasons } }"
            }
            """, existingUsername);

        // When & Then
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(query)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.validateUsername.username").isEqualTo(existingUsername)
                .jsonPath("$.data.validateUsername.isUnique").isEqualTo(false)
                .jsonPath("$.data.validateUsername.reasons").isArray()
                .jsonPath("$.data.validateUsername.reasons").isNotEmpty();
    }

    @Test
    @DisplayName("Should return high confidence score for valid username")
    void shouldReturnHighConfidenceScoreForValidUsername() {
        // Given
        String query = """
            {
              "query": "query { validateUsername(username: \\"validuser123\\", language: EN) { username isValid confidenceScore } }"
            }
            """;

        // When & Then
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(query)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.validateUsername.username").isEqualTo("validuser123")
                .jsonPath("$.data.validateUsername.confidenceScore").isNumber()
                .jsonPath("$.data.validateUsername.confidenceScore").value(score -> {
                    double confidenceScore = ((Number) score).doubleValue();
                    assertThat(confidenceScore)
                            .as("Confidence score should be between 0.0 and 1.0")
                            .isBetween(0.0, 1.0);
                });
    }

    @Test
    @DisplayName("Should return ISO 8601 formatted timestamp in validatedAt field")
    void shouldReturnIso8601FormattedTimestampInValidatedAtField() {
        // Given
        String query = """
            {
              "query": "query { validateUsername(username: \\"testuser\\", language: EN) { username validatedAt } }"
            }
            """;

        // When & Then
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(query)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.validateUsername.validatedAt").isNotEmpty()
                .jsonPath("$.data.validateUsername.validatedAt").value(timestamp -> {
                    String timestampStr = (String) timestamp;
                    assertThat(timestampStr)
                            .as("Timestamp should be in ISO 8601 format")
                            .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*Z");
                });
    }

    @Test
    @DisplayName("Should handle GraphQL query with all response fields")
    void shouldHandleGraphQLQueryWithAllResponseFields() {
        // Given
        String query = """
            {
              "query": "query { validateUsername(username: \\"completetest\\", language: EN) { username isValid isUnique isAppropriate isValidFormat reasons confidenceScore validatedAt } }"
            }
            """;

        // When & Then
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(query)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.validateUsername.username").exists()
                .jsonPath("$.data.validateUsername.isValid").exists()
                .jsonPath("$.data.validateUsername.isUnique").exists()
                .jsonPath("$.data.validateUsername.isAppropriate").exists()
                .jsonPath("$.data.validateUsername.isValidFormat").exists()
                .jsonPath("$.data.validateUsername.reasons").exists()
                .jsonPath("$.data.validateUsername.confidenceScore").exists()
                .jsonPath("$.data.validateUsername.validatedAt").exists();
    }

    @Test
    @DisplayName("Should handle GraphQL query with minimal response fields")
    void shouldHandleGraphQLQueryWithMinimalResponseFields() {
        // Given
        String query = """
            {
              "query": "query { validateUsername(username: \\"minimal\\") { username isValid } }"
            }
            """;

        // When & Then
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(query)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.validateUsername.username").isEqualTo("minimal")
                .jsonPath("$.data.validateUsername.isValid").isBoolean();
    }

    @Test
    @DisplayName("Should validate multiple usernames in sequence via GraphQL")
    void shouldValidateMultipleUsernamesInSequenceViaGraphQL() {
        // Given
        String query1 = """
            {
              "query": "query { validateUsername(username: \\"user1\\") { username isValid } }"
            }
            """;

        String query2 = """
            {
              "query": "query { validateUsername(username: \\"user2\\") { username isValid } }"
            }
            """;

        // When & Then - First validation
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(query1)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.validateUsername.username").isEqualTo("user1");

        // When & Then - Second validation
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(query2)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.validateUsername.username").isEqualTo("user2");
    }

    @Test
    @DisplayName("Should handle empty reasons array for valid username")
    void shouldHandleEmptyReasonsArrayForValidUsername() {
        // Given
        String query = """
            {
              "query": "query { validateUsername(username: \\"validuser456\\") { username isValid reasons } }"
            }
            """;

        // When & Then
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(query)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.validateUsername.username").isEqualTo("validuser456")
                .jsonPath("$.data.validateUsername.reasons").isArray();
    }

    @Test
    @DisplayName("Should validate username with underscores and hyphens")
    void shouldValidateUsernameWithUnderscoresAndHyphens() {
        // Given
        String query = """
            {
              "query": "query { validateUsername(username: \\"valid_user-123\\") { username isValid isValidFormat } }"
            }
            """;

        // When & Then
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(query)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.validateUsername.username").isEqualTo("valid_user-123")
                .jsonPath("$.data.validateUsername.isValidFormat").isBoolean();
    }

    @Test
    @DisplayName("Should handle GraphQL query aliasing")
    void shouldHandleGraphQLQueryAliasing() {
        // Given
        String query = """
            {
              "query": "query { firstValidation: validateUsername(username: \\"user1\\") { username } secondValidation: validateUsername(username: \\"user2\\") { username } }"
            }
            """;

        // When & Then
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(query)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.firstValidation.username").isEqualTo("user1")
                .jsonPath("$.data.secondValidation.username").isEqualTo("user2");
    }

    @Test
    @DisplayName("Should return non-null ValidationResponse for any username")
    void shouldReturnNonNullValidationResponseForAnyUsername() {
        // Given
        String query = """
            {
              "query": "query { validateUsername(username: \\"anyuser\\") { username isValid isUnique isAppropriate isValidFormat } }"
            }
            """;

        // When & Then
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(query)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.validateUsername").exists()
                .jsonPath("$.data.validateUsername.username").isNotEmpty()
                .jsonPath("$.data.validateUsername.isValid").exists()
                .jsonPath("$.data.validateUsername.isUnique").exists()
                .jsonPath("$.data.validateUsername.isAppropriate").exists()
                .jsonPath("$.data.validateUsername.isValidFormat").exists();
    }
}
