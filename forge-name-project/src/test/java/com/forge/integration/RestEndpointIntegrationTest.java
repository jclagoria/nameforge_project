package com.forge.integration;

import com.forge.adapters.inbound.dto.GenerationRequestDto;
import com.forge.application.ForgeNameApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
@DisplayName("REST Endpoint Integration Tests")
class RestEndpointIntegrationTest {

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

        // Clean database and cache before each test
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
        return new String(
                getClass().getClassLoader()
                        .getResourceAsStream("schema.sql")
                        .readAllBytes(),
                StandardCharsets.UTF_8
        );
    }

    @Nested
    @DisplayName("Validate Endpoint Integration Tests")
    class ValidateEndpointTests {

        @Test
        @DisplayName("Should validate username end-to-end with all validation layers")
        void shouldValidateUsernameEndToEndWithAllValidationLayers() {
            // Given
            String username = "validuser_123";

            // When & Then
            webTestClient.get()
                    .uri("/api/v1/usernames/validate/{username}?language=EN", username)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentType(MediaType.APPLICATION_JSON)
                    .expectBody()
                    .jsonPath("$.username").isEqualTo(username)
                    .jsonPath("$.isValid").isEqualTo(true)
                    .jsonPath("$.isValidFormat").isEqualTo(true)
                    .jsonPath("$.isUnique").isEqualTo(true)
                    .jsonPath("$.isAppropriate").isEqualTo(true)
                    .jsonPath("$.reasons").isEmpty()
                    .jsonPath("$.confidenceScore").isNumber()
                    .jsonPath("$.validatedAt").exists();
        }

        @Test
        @DisplayName("Should detect invalid format through validation service")
        void shouldDetectInvalidFormatThroughValidationService() {
            // Given - username too short (minimum is 5 chars)
            String username = "ab";

            // When & Then
            webTestClient.get()
                    .uri("/api/v1/usernames/validate/{username}?language=EN", username)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentType(MediaType.APPLICATION_JSON)
                    .expectBody()
                    .jsonPath("$.username").isEqualTo(username)
                    .jsonPath("$.isValid").isEqualTo(false)
                    .jsonPath("$.isValidFormat").isEqualTo(false)
                    .jsonPath("$.reasons").isNotEmpty();
        }

        @Test
        @DisplayName("Should detect non-unique username from database")
        void shouldDetectNonUniqueUsernameFromDatabase() {
            // Given - insert existing username into database
            String existingUsername = "existing_user";
            databaseClient.sql(
                    "INSERT INTO generated_usernames (username, language, pattern_type, created_at) " +
                    "VALUES (:username, 'EN', 'CLASSIC', NOW())"
            )
            .bind("username", existingUsername)
            .fetch()
            .rowsUpdated()
            .block();

            // When & Then
            webTestClient.get()
                    .uri("/api/v1/usernames/validate/{username}?language=EN", existingUsername)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentType(MediaType.APPLICATION_JSON)
                    .expectBody()
                    .jsonPath("$.username").isEqualTo(existingUsername)
                    .jsonPath("$.isValid").isEqualTo(false)
                    .jsonPath("$.isUnique").isEqualTo(false)
                    .jsonPath("$.reasons").isNotEmpty();
        }

        @Test
        @DisplayName("Should validate with Spanish language parameter")
        void shouldValidateWithSpanishLanguageParameter() {
            // Given
            String username = "usuario_valido";

            // When & Then
            webTestClient.get()
                    .uri("/api/v1/usernames/validate/{username}?language=ES", username)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentType(MediaType.APPLICATION_JSON)
                    .expectBody()
                    .jsonPath("$.username").isEqualTo(username)
                    .jsonPath("$.isValid").isEqualTo(true)
                    .jsonPath("$.isValidFormat").isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("Generate Endpoint Integration Tests")
    class GenerateEndpointTests {

        @Test
        @DisplayName("Should generate usernames end-to-end with persistence")
        void shouldGenerateUsernamesEndToEndWithPersistence() {
            // Given
            GenerationRequestDto request = new GenerationRequestDto("EN", 3);

            // When & Then
            webTestClient.post()
                    .uri("/api/v1/usernames/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentType(MediaType.APPLICATION_JSON)
                    .expectBody()
                    .jsonPath("$.usernames").isArray()
                    .jsonPath("$.usernames.length()").isEqualTo(3)
                    .jsonPath("$.language").isEqualTo("EN")
                    .jsonPath("$.totalGenerated").isEqualTo(3)
                    .jsonPath("$.generatedAt").exists()
                    .jsonPath("$.cacheHit").isEqualTo(false)
                    .jsonPath("$.responseTimeMs").isNumber();

            // Verify persistence in database
            Long count = databaseClient.sql("SELECT COUNT(*) FROM generated_usernames")
                    .fetch()
                    .first()
                    .map(row -> ((Number) row.get("count")).longValue())
                    .block();

            assertThat(count).isEqualTo(3);
        }

        @Test
        @DisplayName("Should use cache on second request with same parameters")
        void shouldUseCacheOnSecondRequestWithSameParameters() {
            // Given
            GenerationRequestDto request = new GenerationRequestDto("EN", 5);

            // First request - should NOT hit cache
            webTestClient.post()
                    .uri("/api/v1/usernames/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.cacheHit").isEqualTo(false)
                    .jsonPath("$.totalGenerated").isEqualTo(5);

            // Second request - should hit cache
            webTestClient.post()
                    .uri("/api/v1/usernames/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.cacheHit").isEqualTo(true)
                    .jsonPath("$.totalGenerated").isEqualTo(5);
        }

        @Test
        @DisplayName("Should generate usernames with Spanish language")
        void shouldGenerateUsernamesWithSpanishLanguage() {
            // Given
            GenerationRequestDto request = new GenerationRequestDto("ES", 2);

            // When & Then
            webTestClient.post()
                    .uri("/api/v1/usernames/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentType(MediaType.APPLICATION_JSON)
                    .expectBody()
                    .jsonPath("$.usernames").isArray()
                    .jsonPath("$.usernames.length()").isEqualTo(2)
                    .jsonPath("$.language").isEqualTo("ES")
                    .jsonPath("$.totalGenerated").isEqualTo(2);

            // Verify language is persisted correctly
            Long count = databaseClient.sql(
                    "SELECT COUNT(*) FROM generated_usernames WHERE language = 'ES'"
            )
            .fetch()
            .first()
            .map(row -> ((Number) row.get("count")).longValue())
            .block();

            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("Should return bad request for invalid generation request")
        void shouldReturnBadRequestForInvalidGenerationRequest() {
            // Given - invalid count (negative)
            GenerationRequestDto request = new GenerationRequestDto("EN", -1);

            // When & Then
            webTestClient.post()
                    .uri("/api/v1/usernames/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }
}
