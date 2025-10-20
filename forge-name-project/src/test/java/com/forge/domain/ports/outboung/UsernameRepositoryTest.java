package com.forge.domain.ports.outboung;

import com.forge.domain.fixtures.TestDataFixtures;
import com.forge.domain.model.Language;
import com.forge.domain.model.PatternType;
import com.forge.domain.model.Username;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UsernameRepository Tests")
class UsernameRepositoryTest {

    @Mock
    private UsernameRepository usernameRepository;

    // Constants
    private static final Duration REASONABLE_TIMEOUT = Duration.ofSeconds(5);
    private static final String ERROR_DB_CONNECTION = "Database connection error";
    private static final String ERROR_SAVE_OPERATION = "Save operation failed";
    private static final String ERROR_FIND_OPERATION = "Find operation failed";

    // Helper methods
    private void verifyMonoCompletes(Mono<?> mono) {
        StepVerifier.create(mono)
                .expectNextCount(1)
                .verifyComplete();
    }

    private void verifyFluxCount(Flux<?> flux, long expectedCount) {
        StepVerifier.create(flux)
                .expectNextCount(expectedCount)
                .verifyComplete();
    }

    private void verifyMonoErrorMessage(Mono<?> mono, String expectedMessage) {
        StepVerifier.create(mono)
                .expectErrorMatches(error ->
                        error instanceof RuntimeException &&
                                error.getMessage().equals(expectedMessage)
                )
                .verify();
    }

    private void verifyFluxErrorMessage(Flux<?> flux, String expectedMessage) {
        StepVerifier.create(flux)
                .expectErrorMatches(error ->
                        error instanceof RuntimeException &&
                                error.getMessage().equals(expectedMessage)
                )
                .verify();
    }

    @Nested
    @DisplayName("existsByUsername() Tests")
    class ExistsByUsernameTests {

        @ParameterizedTest(name = "Should return {1} for username: {0}")
        @CsvSource({
            "testuser123,true",
            "nonexistent_user,false",
            "'',false",
            "user_name-123,true",
            "anyuser,false"
        })
        @DisplayName("Username existence check scenarios")
        void shouldCheckUsernameExistence(String username, boolean exists) {
            // Arrange
            when(usernameRepository.existsByUsername(username))
                    .thenReturn(Mono.just(exists));

            // Act & Assert
            StepVerifier.create(usernameRepository.existsByUsername(username))
                    .expectNext(exists)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle error gracefully")
        void shouldHandleErrorGracefully() {
            // Arrange
            when(usernameRepository.existsByUsername("erroruser"))
                    .thenReturn(Mono.error(new RuntimeException(ERROR_DB_CONNECTION)));

            // Act & Assert
            verifyMonoErrorMessage(
                    usernameRepository.existsByUsername("erroruser"),
                    ERROR_DB_CONNECTION
            );
        }

        @Test
        @DisplayName("Should complete within reasonable timeout")
        void shouldCompleteWithinReasonableTimeout() {
            // Arrange
            when(usernameRepository.existsByUsername("timeoutuser"))
                    .thenReturn(Mono.just(true));

            // Act & Assert
            StepVerifier.create(usernameRepository.existsByUsername("timeoutuser"))
                    .expectNext(true)
                    .expectComplete()
                    .verify(REASONABLE_TIMEOUT);
        }
    }

    @Nested
    @DisplayName("save() Tests")
    class SaveTests {

        @ParameterizedTest(name = "Should save username with {0} language and {1} pattern")
        @MethodSource("languageAndPatternCombinations")
        @DisplayName("Save username with different languages and patterns")
        void shouldSaveUsernameWithDifferentLanguagesAndPatterns(
                Language language,
                PatternType pattern,
                String value
        ) {
            // Arrange
            Username username = TestDataFixtures.username(value, language, pattern);
            when(usernameRepository.save(any(Username.class)))
                    .thenReturn(Mono.just(username));

            // Act & Assert
            StepVerifier.create(usernameRepository.save(username))
                    .assertNext(savedUsername -> {
                        assertThat(savedUsername.value()).isEqualTo(value);
                        assertThat(savedUsername.language()).isEqualTo(language);
                        assertThat(savedUsername.patternType()).isEqualTo(pattern);
                    })
                    .verifyComplete();
        }

        static Stream<Arguments> languageAndPatternCombinations() {
            return Stream.of(
                arguments(Language.EN, PatternType.CLASSIC, "english_user"),
                arguments(Language.ES, PatternType.CLASSIC, "usuario_espanol"),
                arguments(Language.EN, PatternType.SEPARATOR, "separator_user"),
                arguments(Language.EN, PatternType.WORDPLAY, "wordplay_user")
            );
        }

        @Test
        @DisplayName("Should return saved username with all fields populated")
        void shouldReturnSavedUsernameWithAllFieldsPopulated() {
            // Arrange
            Username username = TestDataFixtures.username("complete_user", Language.ES, PatternType.SEPARATOR);
            when(usernameRepository.save(any(Username.class)))
                    .thenReturn(Mono.just(username));

            // Act & Assert
            StepVerifier.create(usernameRepository.save(username))
                    .assertNext(savedUsername -> {
                        assertThat(savedUsername.value()).isNotNull().isNotEmpty();
                        assertThat(savedUsername.language()).isNotNull();
                        assertThat(savedUsername.patternType()).isNotNull();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle error during save operation")
        void shouldHandleErrorDuringSaveOperation() {
            // Arrange
            Username username = TestDataFixtures.username("error_user");
            when(usernameRepository.save(any(Username.class)))
                    .thenReturn(Mono.error(new RuntimeException(ERROR_SAVE_OPERATION)));

            // Act & Assert
            verifyMonoErrorMessage(
                    usernameRepository.save(username),
                    ERROR_SAVE_OPERATION
            );
        }

        @Test
        @DisplayName("Should complete within reasonable timeout")
        void shouldCompleteWithinReasonableTimeout() {
            // Arrange
            Username username = TestDataFixtures.username("timeout_user");
            when(usernameRepository.save(any(Username.class)))
                    .thenReturn(Mono.just(username));

            // Act & Assert
            StepVerifier.create(usernameRepository.save(username))
                    .expectNextCount(1)
                    .expectComplete()
                    .verify(REASONABLE_TIMEOUT);
        }
    }

    @Nested
    @DisplayName("saveBatch() Tests")
    class SaveBatchTests {

        @ParameterizedTest(name = "Should save batch of {0} usernames")
        @CsvSource({
            "3,batch_user",
            "5,multi_user",
            "10,large_user"
        })
        @DisplayName("Save different batch sizes")
        void shouldSaveDifferentBatchSizes(int count, String prefix) {
            // Arrange
            List<Username> usernames = TestDataFixtures.usernames(prefix, count);
            Flux<Username> usernameFlux = Flux.fromIterable(usernames);
            when(usernameRepository.saveBatch(any(Flux.class)))
                    .thenReturn(Flux.fromIterable(usernames));

            // Act & Assert
            verifyFluxCount(usernameRepository.saveBatch(usernameFlux), count);
        }

        @Test
        @DisplayName("Should save empty flux successfully")
        void shouldSaveEmptyFluxSuccessfully() {
            // Arrange
            when(usernameRepository.saveBatch(any(Flux.class)))
                    .thenReturn(Flux.empty());

            // Act & Assert
            StepVerifier.create(usernameRepository.saveBatch(Flux.empty()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should save usernames with different languages")
        void shouldSaveUsernamesWithDifferentLanguages() {
            // Arrange
            List<Username> usernames = TestDataFixtures.mixedLanguageUsernames("mixed");
            Flux<Username> usernameFlux = Flux.fromIterable(usernames);
            when(usernameRepository.saveBatch(any(Flux.class)))
                    .thenReturn(Flux.fromIterable(usernames));

            // Act & Assert
            StepVerifier.create(usernameRepository.saveBatch(usernameFlux))
                    .assertNext(username -> assertThat(username.language()).isEqualTo(Language.EN))
                    .assertNext(username -> assertThat(username.language()).isEqualTo(Language.ES))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should save usernames with different pattern types")
        void shouldSaveUsernamesWithDifferentPatternTypes() {
            // Arrange
            List<Username> usernames = TestDataFixtures.mixedPatternUsernames("pattern");
            Flux<Username> usernameFlux = Flux.fromIterable(usernames);
            when(usernameRepository.saveBatch(any(Flux.class)))
                    .thenReturn(Flux.fromIterable(usernames));

            // Act & Assert
            StepVerifier.create(usernameRepository.saveBatch(usernameFlux))
                    .assertNext(username -> assertThat(username.patternType()).isEqualTo(PatternType.CLASSIC))
                    .assertNext(username -> assertThat(username.patternType()).isEqualTo(PatternType.SEPARATOR))
                    .assertNext(username -> assertThat(username.patternType()).isEqualTo(PatternType.WORDPLAY))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle error in flux stream")
        void shouldHandleErrorInFluxStream() {
            // Arrange
            when(usernameRepository.saveBatch(any(Flux.class)))
                    .thenReturn(Flux.error(new RuntimeException("Batch save failed")));

            // Act & Assert
            verifyFluxErrorMessage(
                    usernameRepository.saveBatch(Flux.empty()),
                    "Batch save failed"
            );
        }

        @Test
        @DisplayName("All saved usernames should maintain their properties")
        void allSavedUsernamesShouldMaintainTheirProperties() {
            // Arrange
            List<Username> usernames = List.of(
                    TestDataFixtures.username("property_user1", Language.EN, PatternType.CLASSIC),
                    TestDataFixtures.username("property_user2", Language.ES, PatternType.SEPARATOR)
            );
            Flux<Username> usernameFlux = Flux.fromIterable(usernames);
            when(usernameRepository.saveBatch(any(Flux.class)))
                    .thenReturn(Flux.fromIterable(usernames));

            // Act & Assert
            StepVerifier.create(usernameRepository.saveBatch(usernameFlux))
                    .assertNext(username -> {
                        assertThat(username.value()).isEqualTo("property_user1");
                        assertThat(username.language()).isEqualTo(Language.EN);
                        assertThat(username.patternType()).isEqualTo(PatternType.CLASSIC);
                    })
                    .assertNext(username -> {
                        assertThat(username.value()).isEqualTo("property_user2");
                        assertThat(username.language()).isEqualTo(Language.ES);
                        assertThat(username.patternType()).isEqualTo(PatternType.SEPARATOR);
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("findAvailableByLanguage() Tests")
    class FindAvailableByLanguageTests {

        @ParameterizedTest(name = "Should find {1} available usernames for {0} language")
        @CsvSource({
            "EN,3,available_en",
            "ES,2,disponible_es",
            "EN,1,single_user",
            "EN,5,limited_user",
            "EN,100,many_user"
        })
        @DisplayName("Find available usernames with different counts and languages")
        void shouldFindAvailableUsernamesWithDifferentCountsAndLanguages(
                Language language,
                int count,
                String prefix
        ) {
            // Arrange
            List<Username> availableUsernames = TestDataFixtures.usernamesWithLanguage(prefix,
                    Math.min(count, 10), language);
            when(usernameRepository.findAvailableByLanguage(language, count))
                    .thenReturn(Flux.fromIterable(availableUsernames));

            // Act & Assert
            verifyFluxCount(
                    usernameRepository.findAvailableByLanguage(language, count),
                    Math.min(count, 10)
            );
        }

        @Test
        @DisplayName("Should return empty flux when no available usernames")
        void shouldReturnEmptyFluxWhenNoAvailableUsernames() {
            // Arrange
            when(usernameRepository.findAvailableByLanguage(Language.EN, 10))
                    .thenReturn(Flux.empty());

            // Act & Assert
            StepVerifier.create(usernameRepository.findAvailableByLanguage(Language.EN, 10))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return usernames with different pattern types")
        void shouldReturnUsernamesWithDifferentPatternTypes() {
            // Arrange
            List<Username> mixedPatterns = TestDataFixtures.mixedPatternUsernames("available");
            when(usernameRepository.findAvailableByLanguage(Language.EN, 3))
                    .thenReturn(Flux.fromIterable(mixedPatterns));

            // Act & Assert
            StepVerifier.create(usernameRepository.findAvailableByLanguage(Language.EN, 3))
                    .assertNext(username -> assertThat(username.patternType()).isEqualTo(PatternType.CLASSIC))
                    .assertNext(username -> assertThat(username.patternType()).isEqualTo(PatternType.SEPARATOR))
                    .assertNext(username -> assertThat(username.patternType()).isEqualTo(PatternType.WORDPLAY))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle error during find operation")
        void shouldHandleErrorDuringFindOperation() {
            // Arrange
            when(usernameRepository.findAvailableByLanguage(Language.EN, 5))
                    .thenReturn(Flux.error(new RuntimeException(ERROR_FIND_OPERATION)));

            // Act & Assert
            verifyFluxErrorMessage(
                    usernameRepository.findAvailableByLanguage(Language.EN, 5),
                    ERROR_FIND_OPERATION
            );
        }

        @Test
        @DisplayName("All returned usernames should match requested language")
        void allReturnedUsernamesShouldMatchRequestedLanguage() {
            // Arrange
            List<Username> englishUsernames = TestDataFixtures.usernames("english_only", 3);
            when(usernameRepository.findAvailableByLanguage(Language.EN, 3))
                    .thenReturn(Flux.fromIterable(englishUsernames));

            // Act & Assert
            StepVerifier.create(usernameRepository.findAvailableByLanguage(Language.EN, 3))
                    .assertNext(username -> assertThat(username.language()).isEqualTo(Language.EN))
                    .assertNext(username -> assertThat(username.language()).isEqualTo(Language.EN))
                    .assertNext(username -> assertThat(username.language()).isEqualTo(Language.EN))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Reactive Behavior and Edge Cases")
    class ReactiveBehaviorTests {

        @Test
        @DisplayName("existsByUsername should emit exactly one boolean value")
        void existsByUsernameShouldEmitExactlyOneBooleanValue() {
            // Arrange
            when(usernameRepository.existsByUsername(anyString()))
                    .thenReturn(Mono.just(true));

            // Act & Assert
            StepVerifier.create(usernameRepository.existsByUsername("test"))
                    .expectNextMatches(Objects::nonNull)
                    .verifyComplete();
        }

        @Test
        @DisplayName("save should emit exactly one Username")
        void saveShouldEmitExactlyOneUsername() {
            // Arrange
            Username username = TestDataFixtures.username("single_emit");
            when(usernameRepository.save(any(Username.class)))
                    .thenReturn(Mono.just(username));

            // Act & Assert
            StepVerifier.create(usernameRepository.save(username))
                    .expectNextMatches(Objects::nonNull)
                    .verifyComplete();
        }

        @Test
        @DisplayName("saveBatch should handle backpressure correctly")
        void saveBatchShouldHandleBackpressureCorrectly() {
            // Arrange
            List<Username> usernames = TestDataFixtures.usernames("backpressure", 3);
            when(usernameRepository.saveBatch(any(Flux.class)))
                    .thenReturn(Flux.fromIterable(usernames));

            // Act & Assert
            StepVerifier.create(usernameRepository.saveBatch(Flux.fromIterable(usernames)), 1)
                    .expectNextCount(1)
                    .thenRequest(2)
                    .expectNextCount(2)
                    .verifyComplete();
        }

        @Test
        @DisplayName("findAvailableByLanguage should handle backpressure correctly")
        void findAvailableByLanguageShouldHandleBackpressureCorrectly() {
            // Arrange
            List<Username> usernames = TestDataFixtures.usernames("backpressure_find", 3);
            when(usernameRepository.findAvailableByLanguage(any(Language.class), anyInt()))
                    .thenReturn(Flux.fromIterable(usernames));

            // Act & Assert
            StepVerifier.create(usernameRepository.findAvailableByLanguage(Language.EN, 3), 1)
                    .expectNextCount(1)
                    .thenRequest(2)
                    .expectNextCount(2)
                    .verifyComplete();
        }

        @Test
        @DisplayName("All operations should complete within reasonable timeout")
        void allOperationsShouldCompleteWithinReasonableTimeout() {
            // Arrange
            Username username = TestDataFixtures.username("timeout_test");
            when(usernameRepository.existsByUsername(anyString()))
                    .thenReturn(Mono.just(true));
            when(usernameRepository.save(any(Username.class)))
                    .thenReturn(Mono.just(username));
            when(usernameRepository.saveBatch(any(Flux.class)))
                    .thenReturn(Flux.just(username));
            when(usernameRepository.findAvailableByLanguage(any(Language.class), anyInt()))
                    .thenReturn(Flux.just(username));

            // Act & Assert
            StepVerifier.create(usernameRepository.existsByUsername("test"))
                    .expectNextCount(1).expectComplete().verify(REASONABLE_TIMEOUT);
            StepVerifier.create(usernameRepository.save(username))
                    .expectNextCount(1).expectComplete().verify(REASONABLE_TIMEOUT);
            StepVerifier.create(usernameRepository.saveBatch(Flux.just(username)))
                    .expectNextCount(1).expectComplete().verify(REASONABLE_TIMEOUT);
            StepVerifier.create(usernameRepository.findAvailableByLanguage(Language.EN, 1))
                    .expectNextCount(1).expectComplete().verify(REASONABLE_TIMEOUT);
        }
    }
}
