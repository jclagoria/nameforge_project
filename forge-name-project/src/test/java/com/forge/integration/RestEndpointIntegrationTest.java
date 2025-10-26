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

        @Test
        @DisplayName("Should return cached validation result on second request with same username and language")
        void shouldReturnCachedValidationResultOnSecondRequestWithSameUsernameAndLanguage() {
            // Given
            String username = "cacheduser123";

            // First request - cache miss, perform full validation
            webTestClient.get()
                    .uri("/api/v1/usernames/validate/{username}?language=EN", username)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.username").isEqualTo(username)
                    .jsonPath("$.isValid").isEqualTo(true);

            // Second request - should hit cache (same username, same language)
            webTestClient.get()
                    .uri("/api/v1/usernames/validate/{username}?language=EN", username)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.username").isEqualTo(username)
                    .jsonPath("$.isValid").isEqualTo(true)
                    .jsonPath("$.isValidFormat").isEqualTo(true)
                    .jsonPath("$.isUnique").isEqualTo(true);

            // Verify Redis cache contains the validation result
            String cacheKey = "validation:usernames" + username.toLowerCase() + ":en:V1";
            Boolean hasKey = redisTemplate.hasKey(cacheKey).block();
            assertThat(hasKey).isTrue();
        }

        @Test
        @DisplayName("Should cache validation results separately for different languages")
        void shouldCacheValidationResultsSeparatelyForDifferentLanguages() {
            // Given
            String username = "multilingualuser";

            // Validate with English
            webTestClient.get()
                    .uri("/api/v1/usernames/validate/{username}?language=EN", username)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.username").isEqualTo(username)
                    .jsonPath("$.isValid").isEqualTo(true);

            // Validate with Spanish
            webTestClient.get()
                    .uri("/api/v1/usernames/validate/{username}?language=ES", username)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.username").isEqualTo(username)
                    .jsonPath("$.isValid").isEqualTo(true);

            // Verify both cache keys exist
            String cacheKeyEN = "validation:usernames" + username.toLowerCase() + ":en:V1";
            String cacheKeyES = "validation:usernames" + username.toLowerCase() + ":es:V1";

            Boolean hasKeyEN = redisTemplate.hasKey(cacheKeyEN).block();
            Boolean hasKeyES = redisTemplate.hasKey(cacheKeyES).block();

            assertThat(hasKeyEN).as("English validation should be cached").isTrue();
            assertThat(hasKeyES).as("Spanish validation should be cached").isTrue();
        }

        @Test
        @DisplayName("Should cache invalid validation results")
        void shouldCacheInvalidValidationResults() {
            // Given - invalid username (too short)
            String invalidUsername = "abc";

            // First request - cache miss
            webTestClient.get()
                    .uri("/api/v1/usernames/validate/{username}?language=EN", invalidUsername)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.username").isEqualTo(invalidUsername)
                    .jsonPath("$.isValid").isEqualTo(false)
                    .jsonPath("$.isValidFormat").isEqualTo(false);

            // Second request - should return cached invalid result
            webTestClient.get()
                    .uri("/api/v1/usernames/validate/{username}?language=EN", invalidUsername)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.username").isEqualTo(invalidUsername)
                    .jsonPath("$.isValid").isEqualTo(false)
                    .jsonPath("$.isValidFormat").isEqualTo(false);

            // Verify cache contains invalid result
            String cacheKey = "validation:usernames" + invalidUsername.toLowerCase() + ":en:V1";
            Boolean hasKey = redisTemplate.hasKey(cacheKey).block();
            assertThat(hasKey).as("Invalid validation result should be cached").isTrue();
        }

        @Test
        @DisplayName("Should cache non-unique username validation results")
        void shouldCacheNonUniqueUsernameValidationResults() {
            // Given - insert existing username into database
            String existingUsername = "cached_existing_user";
            databaseClient.sql(
                    "INSERT INTO generated_usernames (username, language, pattern_type, created_at) " +
                    "VALUES (:username, 'EN', 'CLASSIC', NOW())"
            )
            .bind("username", existingUsername)
            .fetch()
            .rowsUpdated()
            .block();

            // First validation - cache miss, detects non-unique
            webTestClient.get()
                    .uri("/api/v1/usernames/validate/{username}?language=EN", existingUsername)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.username").isEqualTo(existingUsername)
                    .jsonPath("$.isValid").isEqualTo(false)
                    .jsonPath("$.isUnique").isEqualTo(false);

            // Second validation - should return cached non-unique result
            webTestClient.get()
                    .uri("/api/v1/usernames/validate/{username}?language=EN", existingUsername)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.username").isEqualTo(existingUsername)
                    .jsonPath("$.isValid").isEqualTo(false)
                    .jsonPath("$.isUnique").isEqualTo(false);

            // Verify cache contains the result
            String cacheKey = "validation:usernames" + existingUsername.toLowerCase() + ":en:V1";
            Boolean hasKey = redisTemplate.hasKey(cacheKey).block();
            assertThat(hasKey).as("Non-unique validation result should be cached").isTrue();
        }

        @Test
        @DisplayName("Should demonstrate validation performance improvement with cache")
        void shouldDemonstrateValidationPerformanceImprovementWithCache() {
            // Given
            String username = "performancetest123";

            // First request - cache miss (will be slower due to full validation)
            long start1 = System.currentTimeMillis();
            webTestClient.get()
                    .uri("/api/v1/usernames/validate/{username}?language=EN", username)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.username").isEqualTo(username);
            long duration1 = System.currentTimeMillis() - start1;

            // Second request - cache hit (should be faster)
            long start2 = System.currentTimeMillis();
            webTestClient.get()
                    .uri("/api/v1/usernames/validate/{username}?language=EN", username)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.username").isEqualTo(username);
            long duration2 = System.currentTimeMillis() - start2;

            // Cache hit should be faster (or at least not significantly slower)
            assertThat(duration2)
                    .as("Cache hit (%dms) should be faster or similar to cache miss (%dms)",
                            duration2, duration1)
                    .isLessThanOrEqualTo(duration1 + 50); // Allow 50ms margin for test variance
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
