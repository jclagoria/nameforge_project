package com.forge.adapters.outbound.database;

import com.forge.adapters.outbound.database.mapper.UsernameEntityMapper;
import com.forge.domain.model.Language;
import com.forge.domain.model.Username;
import com.forge.domain.ports.outboung.UsernameRepository;
import io.r2dbc.spi.ConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.r2dbc.ConnectionFactoryBuilder;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DisplayName("R2dbcUsernameRepositoryAdapter Tests")
class R2dbcUsernameRepositoryAdapterTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("nameforge_test")
            .withUsername("test")
            .withPassword("test");

    private DatabaseClient databaseClient;
    private R2dbcEntityTemplate entityTemplate;
    private UsernameRepository repository;

    @BeforeEach
    void setUp() throws IOException {
        // Create R2DBC connection manually
        ConnectionFactory connectionFactory = ConnectionFactoryBuilder
                .withUrl("r2dbc:postgresql://" + postgres.getHost() + ":" + postgres.getFirstMappedPort()
                        + "/" + postgres.getDatabaseName())
                .username(postgres.getUsername())
                .password(postgres.getPassword())
                .build();

        databaseClient = DatabaseClient.create(connectionFactory);
        entityTemplate = new R2dbcEntityTemplate(connectionFactory);

        // Create table schema
        String schema = loadSchemaFromClasspath();
        databaseClient.sql(schema)
                .fetch()
                .rowsUpdated()
                .block();

        // Create repository with real mapper
        UsernameEntityMapper mapper = new UsernameEntityMapper();
        repository = new R2dbcUsernameRepositoryAdapter(databaseClient, entityTemplate, mapper);
    }

    @AfterEach
    void tearDown() {
        // Clean database after each test
        databaseClient.sql("DROP TABLE IF EXISTS generated_usernames CASCADE")
                .fetch()
                .rowsUpdated()
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
    @DisplayName("Should Save Username")
    void shouldSaveUsername() {
        // ARRANGE
        Username username = Username.of("cleverpanda42", Language.EN);

        // ACT & ASSERT
        StepVerifier.create(repository.save(username))
                .assertNext(saved -> {
                    assertThat(saved).isNotNull();
                    assertThat(saved.value()).isEqualTo("cleverpanda42");
                    assertThat(saved.language()).isEqualTo(Language.EN);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Return False When Username Does Not Exist")
    void shouldReturnFalseWhenUsernameDoesNotExist() {
        // ACT & ASSERT
        StepVerifier.create(repository.existsByUsername("nonexistent"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Return True When Username Exists")
    void shouldReturnTrueWhenUsernameExists() {
        // ARRANGE
        Username username = Username.of("existinguser42", Language.EN);
        repository.save(username).block();

        // ACT & ASSERT
        StepVerifier.create(repository.existsByUsername("existinguser42"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Find Available Usernames By Language")
    void shouldFindAvailableUsernamesByLanguage() {
        // ARRANGE
        repository.save(Username.of("user1", Language.EN)).block();
        repository.save(Username.of("user2", Language.EN)).block();
        repository.save(Username.of("usuario3", Language.ES)).block();

        // ACT & ASSERT
        StepVerifier.create(repository.findAvailableByLanguage(Language.EN, 10))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Save Batch Of Usernames")
    void shouldSaveBatchOfUsernames() {
        // ARRANGE
        Flux<Username> usernames = Flux.just(
                Username.of("user1", Language.EN),
                Username.of("user2", Language.EN),
                Username.of("user3", Language.EN)
        );

        // ACT & ASSERT
        StepVerifier.create(repository.saveBatch(usernames))
                .expectNextCount(3)
                .verifyComplete();

        // Verify they were saved
        StepVerifier.create(repository.existsByUsername("user1"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Limit Results When Finding Available")
    void shouldLimitResultsWhenFindingAvailable() {
        // ARRANGE
        for (int i = 1; i <= 20; i++) {
            repository.save(Username.of("user" + i, Language.EN)).block();
        }

        // ACT & ASSERT
        StepVerifier.create(repository.findAvailableByLanguage(Language.EN, 5))
                .expectNextCount(5)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Not Find Usernames From Different Language")
    void shouldNotFindUsernamesFromDifferentLanguage() {
        // ARRANGE
        repository.save(Username.of("user1", Language.EN)).block();
        repository.save(Username.of("user2", Language.EN)).block();
        repository.save(Username.of("usuario3", Language.ES)).block();

        // ACT & ASSERT - Search for Spanish, should only find 1
        StepVerifier.create(repository.findAvailableByLanguage(Language.ES, 10))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Return Empty When No Usernames Available For Language")
    void shouldReturnEmptyWhenNoUsernamesAvailableForLanguage() {
        // ARRANGE
        repository.save(Username.of("user1", Language.EN)).block();

        // ACT & ASSERT - Search for language with no data
        StepVerifier.create(repository.findAvailableByLanguage(Language.ES, 10))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Handle Empty Batch Save")
    void shouldHandleEmptyBatchSave() {
        // ARRANGE
        Flux<Username> emptyBatch = Flux.empty();

        // ACT & ASSERT
        StepVerifier.create(repository.saveBatch(emptyBatch))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Save Username With Different Pattern Types")
    void shouldSaveUsernameWithDifferentPatternTypes() {
        // ARRANGE
        Username classic = Username.of("classic123", Language.EN);
        Username modern = Username.of("modern456", Language.EN);

        // ACT & ASSERT
        StepVerifier.create(repository.save(classic))
                .assertNext(saved -> {
                    assertThat(saved).isNotNull();
                    assertThat(saved.value()).isEqualTo("classic123");
                })
                .verifyComplete();

        StepVerifier.create(repository.save(modern))
                .assertNext(saved -> {
                    assertThat(saved).isNotNull();
                    assertThat(saved.value()).isEqualTo("modern456");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Find Available Ordered By Created Date Descending")
    void shouldFindAvailableOrderedByCreatedDateDescending() throws InterruptedException {
        // ARRANGE - Save usernames with small delays to ensure different timestamps
        repository.save(Username.of("oldest", Language.EN)).block();
        Thread.sleep(10);
        repository.save(Username.of("middle", Language.EN)).block();
        Thread.sleep(10);
        repository.save(Username.of("newest", Language.EN)).block();

        // ACT & ASSERT - Should return newest first
        StepVerifier.create(repository.findAvailableByLanguage(Language.EN, 3))
                .assertNext(username -> assertThat(username.value()).isEqualTo("newest"))
                .assertNext(username -> assertThat(username.value()).isEqualTo("middle"))
                .assertNext(username -> assertThat(username.value()).isEqualTo("oldest"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Return Zero Count When Limit Is Zero")
    void shouldReturnZeroCountWhenLimitIsZero() {
        // ARRANGE
        repository.save(Username.of("user1", Language.EN)).block();
        repository.save(Username.of("user2", Language.EN)).block();

        // ACT & ASSERT
        StepVerifier.create(repository.findAvailableByLanguage(Language.EN, 0))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Isolate Languages Correctly")
    void shouldIsolateLanguagesCorrectly() {
        // ARRANGE
        repository.save(Username.of("english1", Language.EN)).block();
        repository.save(Username.of("english2", Language.EN)).block();
        repository.save(Username.of("spanish1", Language.ES)).block();
        repository.save(Username.of("spanish2", Language.ES)).block();

        // ACT & ASSERT - EN should have 2
        StepVerifier.create(repository.findAvailableByLanguage(Language.EN, 10))
                .expectNextCount(2)
                .verifyComplete();

        // ES should have 2
        StepVerifier.create(repository.findAvailableByLanguage(Language.ES, 10))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Verify Batch Save Persists All Items")
    void shouldVerifyBatchSavePersistsAllItems() {
        // ARRANGE
        Flux<Username> usernames = Flux.just(
                Username.of("batch1", Language.EN),
                Username.of("batch2", Language.EN),
                Username.of("batch3", Language.EN)
        );

        // ACT
        repository.saveBatch(usernames).blockLast();

        // ASSERT - All should exist
        StepVerifier.create(repository.existsByUsername("batch1"))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(repository.existsByUsername("batch2"))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(repository.existsByUsername("batch3"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Find Available After Saving Mixed Languages")
    void shouldFindAvailableAfterSavingMixedLanguages() {
        // ARRANGE
        repository.save(Username.of("english1", Language.EN)).block();
        repository.save(Username.of("spanish1", Language.ES)).block();
        repository.save(Username.of("english2", Language.EN)).block();
        repository.save(Username.of("spanish2", Language.ES)).block();

        // ACT & ASSERT
        StepVerifier.create(repository.findAvailableByLanguage(Language.EN, 10))
                .assertNext(u -> assertThat(u.value()).isIn("english1", "english2"))
                .assertNext(u -> assertThat(u.value()).isIn("english1", "english2"))
                .verifyComplete();
    }

    // ==================== markAsUsed() Tests ====================

    @Test
    @DisplayName("Should Mark Username As Used When Username Exists And Is Available")
    void shouldMarkUsernameAsUsedWhenAvailable() {
        // ARRANGE - Save an available username
        String usernameValue = "availableuser123";
        repository.save(Username.of(usernameValue, Language.EN)).block();

        // Verify it's initially not used
        Boolean initiallyUsed = databaseClient
                .sql("SELECT is_used FROM generated_usernames WHERE username = :username")
                .bind("username", usernameValue)
                .map(row -> row.get("is_used", Boolean.class))
                .one()
                .block();
        assertThat(initiallyUsed).isFalse();

        // ACT - Mark as used
        StepVerifier.create(repository.markAsUsed(usernameValue))
                .expectNext(true)
                .verifyComplete();

        // ASSERT - Verify database state changed
        Boolean nowUsed = databaseClient
                .sql("SELECT is_used FROM generated_usernames WHERE username = :username")
                .bind("username", usernameValue)
                .map(row -> row.get("is_used", Boolean.class))
                .one()
                .block();
        assertThat(nowUsed).isTrue();

        // Verify used_at timestamp is set
        Object usedAt = databaseClient
                .sql("SELECT used_at FROM generated_usernames WHERE username = :username")
                .bind("username", usernameValue)
                .map(row -> row.get("used_at"))
                .one()
                .block();
        assertThat(usedAt).isNotNull();
    }

    @Test
    @DisplayName("Should Return False When Username Does Not Exist")
    void shouldReturnFalseWhenMarkingNonExistentUsername() {
        // ACT & ASSERT - Try to mark non-existent username
        StepVerifier.create(repository.markAsUsed("nonexistentuser"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Return False When Username Is Already Marked As Used")
    void shouldReturnFalseWhenUsernameAlreadyMarked() {
        // ARRANGE - Save and mark a username as used
        String usernameValue = "alreadyuseduser";
        repository.save(Username.of(usernameValue, Language.EN)).block();
        repository.markAsUsed(usernameValue).block();

        // Verify it's marked as used
        Boolean isUsed = databaseClient
                .sql("SELECT is_used FROM generated_usernames WHERE username = :username")
                .bind("username", usernameValue)
                .map(row -> row.get("is_used", Boolean.class))
                .one()
                .block();
        assertThat(isUsed).isTrue();

        // ACT - Try to mark again
        StepVerifier.create(repository.markAsUsed(usernameValue))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Not Update Used_At Timestamp When Already Marked")
    void shouldNotUpdateTimestampWhenAlreadyMarked() {
        // ARRANGE - Save and mark username
        String usernameValue = "timestamptest";
        repository.save(Username.of(usernameValue, Language.EN)).block();
        repository.markAsUsed(usernameValue).block();

        // Get initial timestamp
        Object initialTimestamp = databaseClient
                .sql("SELECT used_at FROM generated_usernames WHERE username = :username")
                .bind("username", usernameValue)
                .map(row -> row.get("used_at"))
                .one()
                .block();
        assertThat(initialTimestamp).isNotNull();

        // ACT - Try to mark again
        repository.markAsUsed(usernameValue).block();

        // ASSERT - Timestamp should remain the same (update didn't happen)
        Object finalTimestamp = databaseClient
                .sql("SELECT used_at FROM generated_usernames WHERE username = :username")
                .bind("username", usernameValue)
                .map(row -> row.get("used_at"))
                .one()
                .block();
        assertThat(finalTimestamp).isEqualTo(initialTimestamp);
    }

    @Test
    @DisplayName("Should Mark Multiple Different Usernames Successfully")
    void shouldMarkMultipleDifferentUsernamesSuccessfully() {
        // ARRANGE - Save multiple usernames
        repository.save(Username.of("user1", Language.EN)).block();
        repository.save(Username.of("user2", Language.EN)).block();
        repository.save(Username.of("user3", Language.EN)).block();

        // ACT - Mark each as used
        StepVerifier.create(repository.markAsUsed("user1"))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(repository.markAsUsed("user2"))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(repository.markAsUsed("user3"))
                .expectNext(true)
                .verifyComplete();

        // ASSERT - All should be marked as used
        Long usedCount = databaseClient
                .sql("SELECT COUNT(*) FROM generated_usernames WHERE is_used = TRUE AND username IN ('user1', 'user2', 'user3')")
                .map(row -> row.get(0, Long.class))
                .one()
                .block();
        assertThat(usedCount).isEqualTo(3L);
    }

    @Test
    @DisplayName("Should Not Mark Usernames From Different Languages When Marking One")
    void shouldOnlyMarkSpecificUsername() {
        // ARRANGE - Save usernames with similar patterns
        repository.save(Username.of("testuser", Language.EN)).block();
        repository.save(Username.of("testuser123", Language.EN)).block();

        // ACT - Mark only one
        StepVerifier.create(repository.markAsUsed("testuser"))
                .expectNext(true)
                .verifyComplete();

        // ASSERT - Only "testuser" should be marked, not "testuser123"
        Boolean testUserUsed = databaseClient
                .sql("SELECT is_used FROM generated_usernames WHERE username = 'testuser'")
                .map(row -> row.get("is_used", Boolean.class))
                .one()
                .block();
        assertThat(testUserUsed).isTrue();

        Boolean testUser123Used = databaseClient
                .sql("SELECT is_used FROM generated_usernames WHERE username = 'testuser123'")
                .map(row -> row.get("is_used", Boolean.class))
                .one()
                .block();
        assertThat(testUser123Used).isFalse();
    }

    @Test
    @DisplayName("Should Exclude Marked Usernames From Available Results")
    void shouldExcludeMarkedUsernamesFromAvailableResults() {
        // ARRANGE - Save multiple usernames
        repository.save(Username.of("available1", Language.EN)).block();
        repository.save(Username.of("available2", Language.EN)).block();
        repository.save(Username.of("tobemarked", Language.EN)).block();

        // Mark one as used
        repository.markAsUsed("tobemarked").block();

        // ACT & ASSERT - Should only find the available ones
        StepVerifier.create(repository.findAvailableByLanguage(Language.EN, 10))
                .expectNextCount(2)
                .verifyComplete();

        // Verify the marked one is not in results
        StepVerifier.create(repository.findAvailableByLanguage(Language.EN, 10))
                .assertNext(u -> assertThat(u.value()).isNotEqualTo("tobemarked"))
                .assertNext(u -> assertThat(u.value()).isNotEqualTo("tobemarked"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Handle Empty String Username")
    void shouldHandleEmptyStringUsername() {
        // ACT & ASSERT - Empty string should return false (not found)
        StepVerifier.create(repository.markAsUsed(""))
                .expectNext(false)
                .verifyComplete();
    }

}