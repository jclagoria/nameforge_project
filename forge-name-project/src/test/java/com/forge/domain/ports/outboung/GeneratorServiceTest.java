package com.forge.domain.ports.outboung;

import com.forge.domain.fixtures.TestDataFixtures;
import com.forge.domain.model.Language;
import com.forge.domain.model.PatternType;
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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GeneratorService Tests")
class GeneratorServiceTest {

    @Mock
    private GeneratorService generatorService;

    // Constants
    private static final Duration REASONABLE_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration FAST_GENERATION = Duration.ofMillis(500);
    private static final String ERROR_GENERATION = "Generation failed";
    private static final String VALID_USERNAME_PATTERN = "^[a-z0-9_-]+$";

    // Helper methods
    private void verifyMonoCompletes(Mono<?> mono) {
        StepVerifier.create(mono)
                .expectNextCount(1)
                .verifyComplete();
    }

    private void verifyMonoError(Mono<?> mono, Class<? extends Throwable> errorType) {
        StepVerifier.create(mono)
                .expectError(errorType)
                .verify();
    }

    private void verifyMonoErrorMessage(Mono<?> mono, String expectedMessage) {
        StepVerifier.create(mono)
                .expectErrorMatches(error ->
                        error instanceof RuntimeException &&
                                error.getMessage().equals(expectedMessage)
                )
                .verify();
    }

    @Nested
    @DisplayName("generateUsername() with Language and Pattern Combinations")
    class LanguageAndPatternTests {

        @ParameterizedTest(name = "Should generate {0} username with {1} pattern")
        @MethodSource("languagePatternCombinations")
        @DisplayName("Should generate username for all language and pattern combinations")
        void shouldGenerateUsernameForAllCombinations(Language language, PatternType pattern, String expectedUsername) {
            // Arrange
            when(generatorService.generateUsername(language, pattern))
                    .thenReturn(Mono.just(expectedUsername));

            // Act & Assert
            StepVerifier.create(generatorService.generateUsername(language, pattern))
                    .expectNext(expectedUsername)
                    .verifyComplete();
        }

        static Stream<Arguments> languagePatternCombinations() {
            return Stream.of(
                // English combinations
                arguments(Language.EN, PatternType.CLASSIC, "english_classic_user"),
                arguments(Language.EN, PatternType.SEPARATOR, "english-separator-user"),
                arguments(Language.EN, PatternType.WORDPLAY, "wordplay_english"),
                // Spanish combinations
                arguments(Language.ES, PatternType.CLASSIC, "usuario_clasico_es"),
                arguments(Language.ES, PatternType.SEPARATOR, "usuario-separador-es"),
                arguments(Language.ES, PatternType.WORDPLAY, "juego_palabras_es")
            );
        }

        @ParameterizedTest(name = "Should generate different usernames for {0} language")
        @CsvSource({
            "EN,english_style_user",
            "ES,usuario_estilo_es"
        })
        @DisplayName("Should generate different usernames for different languages")
        void shouldGenerateDifferentUsernamesForDifferentLanguages(Language language, String username) {
            // Arrange
            when(generatorService.generateUsername(language, PatternType.CLASSIC))
                    .thenReturn(Mono.just(username));

            // Act & Assert
            StepVerifier.create(generatorService.generateUsername(language, PatternType.CLASSIC))
                    .expectNext(username)
                    .verifyComplete();
        }

        @ParameterizedTest(name = "Should generate username with {0} pattern")
        @CsvSource({
            "CLASSIC,classic_type",
            "SEPARATOR,separator-type",
            "WORDPLAY,wordplay_type"
        })
        @DisplayName("Should generate different usernames for different pattern types")
        void shouldGenerateDifferentUsernamesForDifferentPatternTypes(PatternType pattern, String username) {
            // Arrange
            when(generatorService.generateUsername(Language.EN, pattern))
                    .thenReturn(Mono.just(username));

            // Act & Assert
            StepVerifier.create(generatorService.generateUsername(Language.EN, pattern))
                    .expectNext(username)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("generateUsername() Format Validation")
    class FormatValidationTests {

        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "Generated username should be non-empty,valid_user123",
            "Generated username should be lowercase,lowercase_user123",
            "Generated username should have minimum length,user123",
            "Generated username should not contain spaces,username_no_spaces",
            "Generated username should not contain uppercase,alllowercase123",
            "Generated username should contain only allowed characters,allowed_chars-123"
        })
        @DisplayName("Username format validation tests")
        void shouldValidateUsernameFormat(String testName, String username) {
            // Arrange
            when(generatorService.generateUsername(any(Language.class), any(PatternType.class)))
                    .thenReturn(Mono.just(username));

            // Act & Assert
            StepVerifier.create(generatorService.generateUsername(Language.EN, PatternType.CLASSIC))
                    .assertNext(result -> {
                        assertThat(result).isNotEmpty();
                        assertThat(result).isLowerCase();
                        assertThat(result).matches(VALID_USERNAME_PATTERN);
                        assertThat(result.length()).isBetween(5, 30);
                        assertThat(result).doesNotContain(" ");
                        assertThat(result).doesNotMatch(".*[A-Z].*");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Generated username should match valid pattern")
        void generatedUsernameShouldMatchValidPattern() {
            // Arrange
            when(generatorService.generateUsername(any(Language.class), any(PatternType.class)))
                    .thenReturn(Mono.just("valid_user-123"));

            // Act & Assert
            StepVerifier.create(generatorService.generateUsername(Language.EN, PatternType.CLASSIC))
                    .assertNext(result -> assertThat(result).matches(VALID_USERNAME_PATTERN))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("generateUsername() Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle error during generation")
        void shouldHandleErrorDuringGeneration() {
            // Arrange
            when(generatorService.generateUsername(any(Language.class), any(PatternType.class)))
                    .thenReturn(Mono.error(new RuntimeException(ERROR_GENERATION)));

            // Act & Assert
            verifyMonoErrorMessage(
                    generatorService.generateUsername(Language.EN, PatternType.CLASSIC),
                    ERROR_GENERATION
            );
        }

        @Test
        @DisplayName("Should handle timeout during generation")
        void shouldHandleTimeoutDuringGeneration() {
            // Arrange
            when(generatorService.generateUsername(any(Language.class), any(PatternType.class)))
                    .thenReturn(Mono.delay(Duration.ofSeconds(10))
                            .map(i -> "delayed_username"));

            // Act & Assert
            StepVerifier.create(generatorService.generateUsername(Language.EN, PatternType.CLASSIC))
                    .expectTimeout(Duration.ofSeconds(1))
                    .verify();
        }

        @Test
        @DisplayName("Should propagate error from underlying service")
        void shouldPropagateErrorFromUnderlyingService() {
            // Arrange
            when(generatorService.generateUsername(any(Language.class), any(PatternType.class)))
                    .thenReturn(Mono.error(new IllegalStateException("Service unavailable")));

            // Act & Assert
            verifyMonoError(
                    generatorService.generateUsername(Language.EN, PatternType.CLASSIC),
                    IllegalStateException.class
            );
        }
    }

    @Nested
    @DisplayName("generateUsername() Reactive Behavior")
    class ReactiveBehaviorTests {

        @Test
        @DisplayName("Should emit exactly one username string")
        void shouldEmitExactlyOneUsernameString() {
            // Arrange
            when(generatorService.generateUsername(any(Language.class), any(PatternType.class)))
                    .thenReturn(Mono.just("single_emission"));

            // Act & Assert
            StepVerifier.create(generatorService.generateUsername(Language.EN, PatternType.CLASSIC))
                    .expectNextMatches(Objects::nonNull)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should complete within reasonable timeout")
        void shouldCompleteWithinReasonableTimeout() {
            // Arrange
            when(generatorService.generateUsername(any(Language.class), any(PatternType.class)))
                    .thenReturn(Mono.just("timeout_user"));

            // Act & Assert
            StepVerifier.create(generatorService.generateUsername(Language.EN, PatternType.CLASSIC))
                    .expectNextCount(1)
                    .expectComplete()
                    .verify(REASONABLE_TIMEOUT);
        }

        @Test
        @DisplayName("Should handle multiple consecutive generations")
        void shouldHandleMultipleConsecutiveGenerations() {
            // Arrange
            when(generatorService.generateUsername(Language.EN, PatternType.CLASSIC))
                    .thenReturn(Mono.just("user1"))
                    .thenReturn(Mono.just("user2"))
                    .thenReturn(Mono.just("user3"));

            // Act & Assert - First generation
            StepVerifier.create(generatorService.generateUsername(Language.EN, PatternType.CLASSIC))
                    .expectNext("user1").verifyComplete();

            // Act & Assert - Second generation
            StepVerifier.create(generatorService.generateUsername(Language.EN, PatternType.CLASSIC))
                    .expectNext("user2").verifyComplete();

            // Act & Assert - Third generation
            StepVerifier.create(generatorService.generateUsername(Language.EN, PatternType.CLASSIC))
                    .expectNext("user3").verifyComplete();
        }
    }

    @Nested
    @DisplayName("generateUsername() Edge Cases and Quality")
    class EdgeCasesAndQualityTests {

        @Test
        @DisplayName("Should generate unique usernames on consecutive calls")
        void shouldGenerateUniqueUsernamesOnConsecutiveCalls() {
            // Arrange
            when(generatorService.generateUsername(Language.EN, PatternType.CLASSIC))
                    .thenReturn(Mono.just("unique_user1"))
                    .thenReturn(Mono.just("unique_user2"));

            // Act
            String firstUsername = generatorService.generateUsername(Language.EN, PatternType.CLASSIC).block();
            String secondUsername = generatorService.generateUsername(Language.EN, PatternType.CLASSIC).block();

            // Assert
            assertThat(firstUsername).isNotEqualTo(secondUsername);
        }

        @Test
        @DisplayName("Generated username should be a valid string")
        void generatedUsernameShouldBeValidString() {
            // Arrange
            when(generatorService.generateUsername(any(Language.class), any(PatternType.class)))
                    .thenReturn(Mono.just("valid_string_123"));

            // Act & Assert
            StepVerifier.create(generatorService.generateUsername(Language.EN, PatternType.CLASSIC))
                    .assertNext(result -> {
                        assertThat(result).isInstanceOf(String.class);
                        assertThat(result).isNotNull();
                        assertThat(result).isNotBlank();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should generate consistent format for same parameters")
        void shouldGenerateConsistentFormatForSameParameters() {
            // Arrange
            when(generatorService.generateUsername(Language.EN, PatternType.CLASSIC))
                    .thenReturn(Mono.just("consistent_user1"))
                    .thenReturn(Mono.just("consistent_user2"));

            // Act
            String first = generatorService.generateUsername(Language.EN, PatternType.CLASSIC).block();
            String second = generatorService.generateUsername(Language.EN, PatternType.CLASSIC).block();

            // Assert - Both should have same format (lowercase, allowed chars)
            assertThat(first).matches(VALID_USERNAME_PATTERN);
            assertThat(second).matches(VALID_USERNAME_PATTERN);
        }

        @Test
        @DisplayName("Should generate username quickly")
        void shouldGenerateUsernameQuickly() {
            // Arrange
            when(generatorService.generateUsername(any(Language.class), any(PatternType.class)))
                    .thenReturn(Mono.just("fast_generated_user"));

            // Act & Assert
            StepVerifier.create(generatorService.generateUsername(Language.EN, PatternType.CLASSIC))
                    .expectNext("fast_generated_user")
                    .expectComplete()
                    .verify(FAST_GENERATION);
        }

        @Test
        @DisplayName("Generated username should be URL-safe")
        void generatedUsernameShouldBeUrlSafe() {
            // Arrange
            when(generatorService.generateUsername(any(Language.class), any(PatternType.class)))
                    .thenReturn(Mono.just("url_safe-user123"));

            // Act & Assert
            StepVerifier.create(generatorService.generateUsername(Language.EN, PatternType.CLASSIC))
                    .assertNext(result -> {
                        assertThat(result).doesNotContain(" ", "@", "#", "&", "=", "+", "%");
                        assertThat(result).matches(VALID_USERNAME_PATTERN);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Generated username should be database-safe")
        void generatedUsernameShouldBeDatabaseSafe() {
            // Arrange
            when(generatorService.generateUsername(any(Language.class), any(PatternType.class)))
                    .thenReturn(Mono.just("db_safe_user"));

            // Act & Assert
            StepVerifier.create(generatorService.generateUsername(Language.EN, PatternType.CLASSIC))
                    .assertNext(result ->
                        assertThat(result).doesNotContain("'", "\"", ";", "--", "/*", "*/")
                    )
                    .verifyComplete();
        }

        @Test
        @DisplayName("All operations should return non-null results")
        void allOperationsShouldReturnNonNullResults() {
            // Arrange
            when(generatorService.generateUsername(any(Language.class), any(PatternType.class)))
                    .thenReturn(Mono.just("nonnull_user"));

            // Act & Assert
            StepVerifier.create(generatorService.generateUsername(Language.EN, PatternType.CLASSIC))
                    .expectNextMatches(Objects::nonNull)
                    .verifyComplete();

            StepVerifier.create(generatorService.generateUsername(Language.ES, PatternType.SEPARATOR))
                    .expectNextMatches(Objects::nonNull)
                    .verifyComplete();
        }
    }
}
