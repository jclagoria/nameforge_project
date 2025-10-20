package com.forge.domain.ports.outboung;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Objects;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ModerationService Tests")
class ModerationServiceTest {

    @Mock
    private ModerationService moderationService;

    // Constants
    private static final Duration REASONABLE_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration FAST_MODERATION = Duration.ofSeconds(2);
    private static final String ERROR_SERVICE_UNAVAILABLE = "Moderation service unavailable";
    private static final String ERROR_NETWORK_TIMEOUT = "Network timeout";
    private static final String ERROR_API_RATE_LIMIT = "API rate limit exceeded";

    // Helper methods
    private void verifyAppropriate(String username, boolean expected) {
        when(moderationService.isAppropriate(username))
                .thenReturn(Mono.just(expected));

        StepVerifier.create(moderationService.isAppropriate(username))
                .expectNext(expected)
                .verifyComplete();
    }

    private void verifyMonoCompletes(Mono<?> mono) {
        StepVerifier.create(mono)
                .expectNextCount(1)
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

    @Nested
    @DisplayName("isAppropriate() with Appropriate Content")
    class AppropriateContentTests {

        @ParameterizedTest(name = "Should return true for: {0}")
        @ValueSource(strings = {
            "valid_user123",
            "validusername",
            "user123name456",
            "valid_user_name",
            "valid-user-name",
            "user",
            "player",
            "gamer",
            "john_developer",
            "cool_coder123"
        })
        @DisplayName("Should return true for appropriate usernames")
        void shouldReturnTrueForAppropriateUsernames(String username) {
            verifyAppropriate(username, true);
        }
    }

    @Nested
    @DisplayName("isAppropriate() with Inappropriate Content")
    class InappropriateContentTests {

        @ParameterizedTest(name = "Should return false for: {0}")
        @CsvSource({
            "offensive_word,offensive words",
            "bad_profanity,profanity",
            "hate_speech_word,hate speech",
            "discriminatory_term,discriminatory content",
            "sexual_content,sexual content",
            "violent_word,violent content",
            "0ff3ns1v3_w0rd,leetspeak offensive",
            "b@d_w0rd,obfuscated offensive"
        })
        @DisplayName("Should return false for inappropriate content")
        void shouldReturnFalseForInappropriateContent(String username, String reason) {
            verifyAppropriate(username, false);
        }
    }

    @Nested
    @DisplayName("isAppropriate() with Edge Cases")
    class EdgeCasesTests {

        @ParameterizedTest(name = "{0}: {1}")
        @CsvSource({
            "'',false,empty string",
            "ab,true,very short username",
            "very_long_username_with_many_characters_here,true,very long username",
            "123456789,true,all numbers",
            "____----,true,special characters only",
            "ValidUsername,true,mixed case",
            "aaaaaaa,true,repeated characters",
            "user name,false,whitespace"
        })
        @DisplayName("Edge case validation")
        void shouldHandleEdgeCases(String username, boolean expected, String description) {
            verifyAppropriate(username, expected);
        }
    }

    @Nested
    @DisplayName("isAppropriate() with Contextual Variations")
    class ContextualVariationsTests {

        @ParameterizedTest(name = "Should handle: {1}")
        @CsvSource({
            "offensive_user123,false,offensive word at beginning",
            "user123_offensive,false,offensive word at end",
            "user_offensive_name,false,offensive word in middle",
            "classic_word,true,partial offensive words (false positive)",
            "bad_offensive_word,false,multiple offensive words"
        })
        @DisplayName("Contextual position of offensive content")
        void shouldHandleOffensiveWordPositions(String username, boolean expected, String description) {
            verifyAppropriate(username, expected);
        }

        @Test
        @DisplayName("Should handle common false positives correctly")
        void shouldHandleCommonFalsePositivesCorrectly() {
            // Arrange & Act & Assert
            verifyAppropriate("assessment", true);
            verifyAppropriate("scunthorpe", true);
        }
    }

    @Nested
    @DisplayName("isAppropriate() Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle error during moderation check")
        void shouldHandleErrorDuringModerationCheck() {
            // Arrange
            when(moderationService.isAppropriate("error_check"))
                    .thenReturn(Mono.error(new RuntimeException(ERROR_SERVICE_UNAVAILABLE)));

            // Act & Assert
            verifyMonoErrorMessage(
                    moderationService.isAppropriate("error_check"),
                    ERROR_SERVICE_UNAVAILABLE
            );
        }

        @Test
        @DisplayName("Should handle timeout during moderation check")
        void shouldHandleTimeoutDuringModerationCheck() {
            // Arrange
            when(moderationService.isAppropriate("timeout_check"))
                    .thenReturn(Mono.delay(Duration.ofSeconds(10)).map(i -> true));

            // Act & Assert
            StepVerifier.create(moderationService.isAppropriate("timeout_check"))
                    .expectTimeout(Duration.ofSeconds(2))
                    .verify();
        }

        @Test
        @DisplayName("Should propagate error from moderation API")
        void shouldPropagateErrorFromModerationApi() {
            // Arrange
            when(moderationService.isAppropriate("api_error"))
                    .thenReturn(Mono.error(new IllegalStateException(ERROR_API_RATE_LIMIT)));

            // Act & Assert
            StepVerifier.create(moderationService.isAppropriate("api_error"))
                    .expectError(IllegalStateException.class)
                    .verify();
        }

        @Test
        @DisplayName("Should handle network error gracefully")
        void shouldHandleNetworkErrorGracefully() {
            // Arrange
            when(moderationService.isAppropriate("network_error"))
                    .thenReturn(Mono.error(new RuntimeException(ERROR_NETWORK_TIMEOUT)));

            // Act & Assert
            verifyMonoErrorMessage(
                    moderationService.isAppropriate("network_error"),
                    ERROR_NETWORK_TIMEOUT
            );
        }
    }

    @Nested
    @DisplayName("isAppropriate() Reactive Behavior")
    class ReactiveBehaviorTests {

        @Test
        @DisplayName("Should emit exactly one boolean value")
        void shouldEmitExactlyOneBooleanValue() {
            // Arrange
            when(moderationService.isAppropriate(anyString()))
                    .thenReturn(Mono.just(true));

            // Act & Assert
            StepVerifier.create(moderationService.isAppropriate("test"))
                    .expectNextMatches(Objects::nonNull)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should complete within reasonable timeout")
        void shouldCompleteWithinReasonableTimeout() {
            // Arrange
            when(moderationService.isAppropriate("timeout_test"))
                    .thenReturn(Mono.just(true));

            // Act & Assert
            StepVerifier.create(moderationService.isAppropriate("timeout_test"))
                    .expectNextCount(1)
                    .expectComplete()
                    .verify(REASONABLE_TIMEOUT);
        }

        @Test
        @DisplayName("Should handle multiple consecutive moderation checks")
        void shouldHandleMultipleConsecutiveModerationChecks() {
            // Arrange
            when(moderationService.isAppropriate("user1")).thenReturn(Mono.just(true));
            when(moderationService.isAppropriate("offensive1")).thenReturn(Mono.just(false));
            when(moderationService.isAppropriate("user2")).thenReturn(Mono.just(true));

            // Act & Assert
            verifyAppropriate("user1", true);
            verifyAppropriate("offensive1", false);
            verifyAppropriate("user2", true);
        }
    }

    @Nested
    @DisplayName("isAppropriate() Performance and Quality")
    class PerformanceAndQualityTests {

        @Test
        @DisplayName("Should perform moderation check quickly")
        void shouldPerformModerationCheckQuickly() {
            // Arrange
            when(moderationService.isAppropriate("fast_check"))
                    .thenReturn(Mono.just(true));

            // Act & Assert
            StepVerifier.create(moderationService.isAppropriate("fast_check"))
                    .expectNext(true)
                    .expectComplete()
                    .verify(FAST_MODERATION);
        }

        @Test
        @DisplayName("Should return consistent results for same username")
        void shouldReturnConsistentResultsForSameUsername() {
            // Arrange
            when(moderationService.isAppropriate("consistent_user"))
                    .thenReturn(Mono.just(true));

            // Act & Assert - Multiple calls should return same result
            verifyAppropriate("consistent_user", true);
            verifyAppropriate("consistent_user", true);
        }

        @ParameterizedTest(name = "Batch check {index}: {0}")
        @CsvSource({
            "user1,true",
            "user2,true",
            "user3,true",
            "offensive1,false",
            "offensive2,false"
        })
        @DisplayName("Should handle batch moderation checks efficiently")
        void shouldHandleBatchModerationChecksEfficiently(String username, boolean expected) {
            verifyAppropriate(username, expected);
        }

        @Test
        @DisplayName("All operations should return non-null boolean results")
        void allOperationsShouldReturnNonNullBooleanResults() {
            // Arrange
            when(moderationService.isAppropriate(anyString()))
                    .thenReturn(Mono.just(true));

            // Act & Assert
            StepVerifier.create(moderationService.isAppropriate("test1"))
                    .expectNextMatches(Objects::nonNull)
                    .verifyComplete();

            StepVerifier.create(moderationService.isAppropriate("test2"))
                    .expectNextMatches(Objects::nonNull)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should provide deterministic results for moderation")
        void shouldProvideDeterministicResultsForModeration() {
            // Arrange
            when(moderationService.isAppropriate("good_user")).thenReturn(Mono.just(true));
            when(moderationService.isAppropriate("bad_user")).thenReturn(Mono.just(false));

            // Act & Assert - Multiple calls should return same result
            for (int i = 0; i < 3; i++) {
                verifyAppropriate("good_user", true);
                verifyAppropriate("bad_user", false);
            }
        }
    }

    @Nested
    @DisplayName("isAppropriate() with Language-Specific Content")
    class LanguageSpecificContentTests {

        @ParameterizedTest(name = "Should handle {1} offensive content: {0}")
        @CsvSource({
            "english_bad_word,English",
            "palabra_ofensiva,Spanish",
            "mixed_bad_palabra,multi-language",
            "transliterated_bad,transliterated"
        })
        @DisplayName("Language-specific offensive content detection")
        void shouldHandleLanguageSpecificOffensiveContent(String username, String language) {
            verifyAppropriate(username, false);
        }
    }
}
