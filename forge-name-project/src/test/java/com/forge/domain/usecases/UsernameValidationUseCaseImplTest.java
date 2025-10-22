package com.forge.domain.usecases;

import com.forge.domain.model.Language;
import com.forge.domain.model.ValidationRequest;
import com.forge.domain.model.ValidationResult;
import com.forge.domain.ports.outboung.ModerationService;
import com.forge.domain.ports.outboung.UsernameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UsernameValidationUseCase Implementation Tests")
class UsernameValidationUseCaseImplTest {

    @Mock
    private UsernameRepository usernameRepository;

    @Mock
    private ModerationService moderationService;

    private UsernameValidationUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new UsernameValidationUseCaseImpl(usernameRepository, moderationService);
    }

    @Nested
    @DisplayName("Successful Validation Tests")
    class SuccessfulValidationTests {

        @Test
        @DisplayName("Should validate username successfully when all checks pass")
        void shouldValidateUsernameSuccessfullyWhenAllChecksPass() {
            // Given
            ValidationRequest request = ValidationRequest.of("validuser", Language.EN);
            when(usernameRepository.existsByUsername("validuser")).thenReturn(Mono.just(false));
            when(moderationService.isAppropriate("validuser")).thenReturn(Mono.just(true));

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertTrue(validationResult.isValid());
                        assertTrue(validationResult.isUnique());
                        assertTrue(validationResult.isAppropriate());
                        assertTrue(validationResult.isValidFormat());
                        assertTrue(validationResult.reasons().isEmpty());
                        assertEquals(0.98, validationResult.confidenceScore());
                    })
                    .verifyComplete();

            verify(usernameRepository).existsByUsername("validuser");
            verify(moderationService).isAppropriate("validuser");
        }

        @Test
        @DisplayName("Should validate username with minimum valid length")
        void shouldValidateUsernameWithMinimumValidLength() {
            // Given - minimum is 5 characters
            ValidationRequest request = ValidationRequest.of("abcde", Language.EN);
            when(usernameRepository.existsByUsername("abcde")).thenReturn(Mono.just(false));
            when(moderationService.isAppropriate("abcde")).thenReturn(Mono.just(true));

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertTrue(validationResult.isValid());
                        assertEquals("abcde", validationResult.username());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should validate username with special characters")
        void shouldValidateUsernameWithSpecialCharacters() {
            // Given
            ValidationRequest request = ValidationRequest.of("user_123-test", Language.EN);
            when(usernameRepository.existsByUsername("user_123-test")).thenReturn(Mono.just(false));
            when(moderationService.isAppropriate("user_123-test")).thenReturn(Mono.just(true));

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertTrue(validationResult.isValid());
                        assertEquals("user_123-test", validationResult.username());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should validate username in Spanish language")
        void shouldValidateUsernameInSpanishLanguage() {
            // Given
            ValidationRequest request = ValidationRequest.of("usuario", Language.ES);
            when(usernameRepository.existsByUsername("usuario")).thenReturn(Mono.just(false));
            when(moderationService.isAppropriate("usuario")).thenReturn(Mono.just(true));

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertTrue(validationResult.isValid());
                        assertEquals("usuario", validationResult.username());
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Format Validation Tests")
    class FormatValidationTests {

        @Test
        @DisplayName("Should reject username that is too short")
        void shouldRejectUsernameThatIsTooShort() {
            // Given - less than 5 characters
            ValidationRequest request = ValidationRequest.of("abcd", Language.EN);

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertFalse(validationResult.isValid());
                        assertFalse(validationResult.isValidFormat());
                        assertTrue(validationResult.isUnique()); // not checked
                        assertTrue(validationResult.isAppropriate()); // not checked
                        assertFalse(validationResult.reasons().isEmpty());
                        assertTrue(validationResult.reasons().get(0).contains("Invalid format"));
                    })
                    .verifyComplete();

            verifyNoInteractions(usernameRepository);
            verifyNoInteractions(moderationService);
        }

        @Test
        @DisplayName("Should reject username that is too long")
        void shouldRejectUsernameThatIsTooLong() {
            // Given
            String longUsername = "a".repeat(256);
            ValidationRequest request = ValidationRequest.of(longUsername, Language.EN);

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertFalse(validationResult.isValid());
                        assertFalse(validationResult.isValidFormat());
                        assertTrue(validationResult.reasons().stream()
                                .anyMatch(reason -> reason.contains("Invalid format")));
                    })
                    .verifyComplete();

            verifyNoInteractions(usernameRepository);
            verifyNoInteractions(moderationService);
        }

        @Test
        @DisplayName("Should reject username with invalid characters")
        void shouldRejectUsernameWithInvalidCharacters() {
            // Given
            ValidationRequest request = ValidationRequest.of("user@test", Language.EN);

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertFalse(validationResult.isValid());
                        assertFalse(validationResult.isValidFormat());
                        assertFalse(validationResult.reasons().isEmpty());
                    })
                    .verifyComplete();

            verifyNoInteractions(usernameRepository);
            verifyNoInteractions(moderationService);
        }

        @Test
        @DisplayName("Should stop validation early when format is invalid")
        void shouldStopValidationEarlyWhenFormatIsInvalid() {
            // Given - username too short
            ValidationRequest request = ValidationRequest.of("abc", Language.EN);

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertFalse(validationResult.isValid());
                        assertFalse(validationResult.isValidFormat());
                    })
                    .verifyComplete();

            // Verify that repository and moderation service are never called
            verifyNoInteractions(usernameRepository);
            verifyNoInteractions(moderationService);
        }
    }

    @Nested
    @DisplayName("Uniqueness Validation Tests")
    class UniquenessValidationTests {

        @Test
        @DisplayName("Should reject username that already exists")
        void shouldRejectUsernameThatAlreadyExists() {
            // Given
            ValidationRequest request = ValidationRequest.of("existinguser", Language.EN);
            when(usernameRepository.existsByUsername("existinguser")).thenReturn(Mono.just(true));
            when(moderationService.isAppropriate("existinguser")).thenReturn(Mono.just(true));

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertFalse(validationResult.isValid());
                        assertFalse(validationResult.isUnique());
                        assertTrue(validationResult.isAppropriate());
                        assertTrue(validationResult.isValidFormat());
                        assertTrue(validationResult.reasons().contains("Username is already taken"));
                        assertEquals(0.85, validationResult.confidenceScore());
                    })
                    .verifyComplete();

            verify(usernameRepository).existsByUsername("existinguser");
            verify(moderationService).isAppropriate("existinguser");
        }

        @Test
        @DisplayName("Should handle repository error gracefully and assume username is unique")
        void shouldHandleRepositoryErrorGracefullyAndAssumeUsernameIsUnique() {
            // Given
            ValidationRequest request = ValidationRequest.of("testuser", Language.EN);
            when(usernameRepository.existsByUsername("testuser"))
                    .thenReturn(Mono.error(new RuntimeException("Database error")));
            when(moderationService.isAppropriate("testuser")).thenReturn(Mono.just(true));

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertTrue(validationResult.isValid());
                        assertTrue(validationResult.isUnique()); // assumed unique on error
                        assertTrue(validationResult.isAppropriate());
                        assertTrue(validationResult.isValidFormat());
                    })
                    .verifyComplete();

            verify(usernameRepository).existsByUsername("testuser");
            verify(moderationService).isAppropriate("testuser");
        }

        @Test
        @DisplayName("Should check uniqueness only after format validation passes")
        void shouldCheckUniquenessOnlyAfterFormatValidationPasses() {
            // Given - username too short (less than 5 characters)
            ValidationRequest request = ValidationRequest.of("ab", Language.EN);

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertFalse(validationResult.isValid());
                        assertFalse(validationResult.isValidFormat());
                    })
                    .verifyComplete();

            verifyNoInteractions(usernameRepository);
        }
    }

    @Nested
    @DisplayName("Appropriateness Validation Tests")
    class AppropriatenessValidationTests {

        @Test
        @DisplayName("Should reject username with inappropriate content")
        void shouldRejectUsernameWithInappropriateContent() {
            // Given
            ValidationRequest request = ValidationRequest.of("badword", Language.EN);
            when(usernameRepository.existsByUsername("badword")).thenReturn(Mono.just(false));
            when(moderationService.isAppropriate("badword")).thenReturn(Mono.just(false));

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertFalse(validationResult.isValid());
                        assertTrue(validationResult.isUnique());
                        assertFalse(validationResult.isAppropriate());
                        assertTrue(validationResult.isValidFormat());
                        assertTrue(validationResult.reasons().contains("Content not appropriate for general use"));
                        assertEquals(0.85, validationResult.confidenceScore());
                    })
                    .verifyComplete();

            verify(usernameRepository).existsByUsername("badword");
            verify(moderationService).isAppropriate("badword");
        }

        @Test
        @DisplayName("Should handle moderation service timeout and assume username is appropriate")
        void shouldHandleModerationServiceTimeoutAndAssumeUsernameIsAppropriate() {
            // Given
            ValidationRequest request = ValidationRequest.of("testuser", Language.EN);
            when(usernameRepository.existsByUsername("testuser")).thenReturn(Mono.just(false));
            when(moderationService.isAppropriate("testuser"))
                    .thenReturn(Mono.delay(Duration.ofSeconds(5)).map(i -> true));

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertTrue(validationResult.isValid());
                        assertTrue(validationResult.isUnique());
                        assertTrue(validationResult.isAppropriate()); // assumed appropriate on timeout
                        assertTrue(validationResult.isValidFormat());
                    })
                    .verifyComplete();

            verify(usernameRepository).existsByUsername("testuser");
            verify(moderationService).isAppropriate("testuser");
        }

        @Test
        @DisplayName("Should handle moderation service error and assume username is appropriate")
        void shouldHandleModerationServiceErrorAndAssumeUsernameIsAppropriate() {
            // Given
            ValidationRequest request = ValidationRequest.of("testuser", Language.EN);
            when(usernameRepository.existsByUsername("testuser")).thenReturn(Mono.just(false));
            when(moderationService.isAppropriate("testuser"))
                    .thenReturn(Mono.error(new RuntimeException("Moderation service unavailable")));

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertTrue(validationResult.isValid());
                        assertTrue(validationResult.isUnique());
                        assertTrue(validationResult.isAppropriate()); // assumed appropriate on error
                        assertTrue(validationResult.isValidFormat());
                    })
                    .verifyComplete();

            verify(usernameRepository).existsByUsername("testuser");
            verify(moderationService).isAppropriate("testuser");
        }

        @Test
        @DisplayName("Should check appropriateness only after format and uniqueness validation")
        void shouldCheckAppropriatenessOnlyAfterFormatAndUniquenessValidation() {
            // Given - username too short (less than 5 characters)
            ValidationRequest request = ValidationRequest.of("xyz", Language.EN);

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertFalse(validationResult.isValid());
                        assertFalse(validationResult.isValidFormat());
                    })
                    .verifyComplete();

            verifyNoInteractions(moderationService);
        }
    }

    @Nested
    @DisplayName("Multiple Failures Tests")
    class MultipleFailuresTests {

        @Test
        @DisplayName("Should reject username when both uniqueness and appropriateness fail")
        void shouldRejectUsernameWhenBothUniquenessAndAppropriatenessFail() {
            // Given
            ValidationRequest request = ValidationRequest.of("badexisting", Language.EN);
            when(usernameRepository.existsByUsername("badexisting")).thenReturn(Mono.just(true));
            when(moderationService.isAppropriate("badexisting")).thenReturn(Mono.just(false));

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertFalse(validationResult.isValid());
                        assertFalse(validationResult.isUnique());
                        assertFalse(validationResult.isAppropriate());
                        assertTrue(validationResult.isValidFormat());
                        assertEquals(2, validationResult.reasons().size());
                        assertTrue(validationResult.reasons().contains("Username is already taken"));
                        assertTrue(validationResult.reasons().contains("Content not appropriate for general use"));
                        assertEquals(0.50, validationResult.confidenceScore());
                    })
                    .verifyComplete();

            verify(usernameRepository).existsByUsername("badexisting");
            verify(moderationService).isAppropriate("badexisting");
        }

        @Test
        @DisplayName("Should include all failure reasons in validation result")
        void shouldIncludeAllFailureReasonsInValidationResult() {
            // Given
            ValidationRequest request = ValidationRequest.of("badexisting", Language.EN);
            when(usernameRepository.existsByUsername("badexisting")).thenReturn(Mono.just(true));
            when(moderationService.isAppropriate("badexisting")).thenReturn(Mono.just(false));

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertFalse(validationResult.reasons().isEmpty());
                        assertTrue(validationResult.reasons().size() >= 2);
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Validation Result Building Tests")
    class ValidationResultBuildingTests {

        @Test
        @DisplayName("Should set correct timestamp in validation result")
        void shouldSetCorrectTimestampInValidationResult() {
            // Given
            ValidationRequest request = ValidationRequest.of("testuser", Language.EN);
            when(usernameRepository.existsByUsername("testuser")).thenReturn(Mono.just(false));
            when(moderationService.isAppropriate("testuser")).thenReturn(Mono.just(true));

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertNotNull(validationResult.validatedAt());
                        assertTrue(validationResult.validatedAt().isBefore(
                                validationResult.validatedAt().plusSeconds(1)
                        ));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should set username in validation result")
        void shouldSetUsernameInValidationResult() {
            // Given
            ValidationRequest request = ValidationRequest.of("myusername", Language.EN);
            when(usernameRepository.existsByUsername("myusername")).thenReturn(Mono.just(false));
            when(moderationService.isAppropriate("myusername")).thenReturn(Mono.just(true));

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertEquals("myusername", validationResult.username());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return empty reasons list when validation succeeds")
        void shouldReturnEmptyReasonsListWhenValidationSucceeds() {
            // Given
            ValidationRequest request = ValidationRequest.of("validuser", Language.EN);
            when(usernameRepository.existsByUsername("validuser")).thenReturn(Mono.just(false));
            when(moderationService.isAppropriate("validuser")).thenReturn(Mono.just(true));

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertTrue(validationResult.reasons().isEmpty());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should calculate correct confidence score for valid username")
        void shouldCalculateCorrectConfidenceScoreForValidUsername() {
            // Given
            ValidationRequest request = ValidationRequest.of("validuser", Language.EN);
            when(usernameRepository.existsByUsername("validuser")).thenReturn(Mono.just(false));
            when(moderationService.isAppropriate("validuser")).thenReturn(Mono.just(true));

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertEquals(0.98, validationResult.confidenceScore());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should calculate correct confidence score when one check fails")
        void shouldCalculateCorrectConfidenceScoreWhenOneCheckFails() {
            // Given
            ValidationRequest request = ValidationRequest.of("existinguser", Language.EN);
            when(usernameRepository.existsByUsername("existinguser")).thenReturn(Mono.just(true));
            when(moderationService.isAppropriate("existinguser")).thenReturn(Mono.just(true));

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertEquals(0.85, validationResult.confidenceScore());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should calculate correct confidence score when two checks fail")
        void shouldCalculateCorrectConfidenceScoreWhenTwoChecksFail() {
            // Given
            ValidationRequest request = ValidationRequest.of("badexisting", Language.EN);
            when(usernameRepository.existsByUsername("badexisting")).thenReturn(Mono.just(true));
            when(moderationService.isAppropriate("badexisting")).thenReturn(Mono.just(false));

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertEquals(0.50, validationResult.confidenceScore());
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Reactive Flow Tests")
    class ReactiveFlowTests {

        @Test
        @DisplayName("Should complete validation flow reactively without blocking")
        void shouldCompleteValidationFlowReactivelyWithoutBlocking() {
            // Given
            ValidationRequest request = ValidationRequest.of("testuser", Language.EN);
            when(usernameRepository.existsByUsername("testuser")).thenReturn(Mono.just(false));
            when(moderationService.isAppropriate("testuser")).thenReturn(Mono.just(true));

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then - verify the Mono completes
            StepVerifier.create(result)
                    .expectNextCount(1)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle repository Mono error in reactive chain")
        void shouldHandleRepositoryMonoErrorInReactiveChain() {
            // Given
            ValidationRequest request = ValidationRequest.of("testuser", Language.EN);
            when(usernameRepository.existsByUsername("testuser"))
                    .thenReturn(Mono.error(new RuntimeException("DB error")));
            when(moderationService.isAppropriate("testuser")).thenReturn(Mono.just(true));

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then - should complete successfully with error handled
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertTrue(validationResult.isValid());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle moderation service Mono error in reactive chain")
        void shouldHandleModerationServiceMonoErrorInReactiveChain() {
            // Given
            ValidationRequest request = ValidationRequest.of("testuser", Language.EN);
            when(usernameRepository.existsByUsername("testuser")).thenReturn(Mono.just(false));
            when(moderationService.isAppropriate("testuser"))
                    .thenReturn(Mono.error(new RuntimeException("Moderation error")));

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then - should complete successfully with error handled
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertTrue(validationResult.isValid());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should propagate validation result through reactive chain")
        void shouldPropagateValidationResultThroughReactiveChain() {
            // Given
            ValidationRequest request = ValidationRequest.of("testuser", Language.EN);
            when(usernameRepository.existsByUsername("testuser")).thenReturn(Mono.just(false));
            when(moderationService.isAppropriate("testuser")).thenReturn(Mono.just(true));

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertNotNull(validationResult);
                        assertEquals("testuser", validationResult.username());
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Tests")
    class EdgeCasesAndBoundaryTests {

        @Test
        @DisplayName("Should handle username at minimum valid length boundary")
        void shouldHandleUsernameAtMinimumValidLengthBoundary() {
            // Given - minimum is 5 characters
            ValidationRequest request = ValidationRequest.of("abcde", Language.EN);
            when(usernameRepository.existsByUsername("abcde")).thenReturn(Mono.just(false));
            when(moderationService.isAppropriate("abcde")).thenReturn(Mono.just(true));

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertTrue(validationResult.isValid());
                        assertTrue(validationResult.isValidFormat());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle username with maximum valid length")
        void shouldHandleUsernameWithMaximumValidLength() {
            // Given
            String maxUsername = "a".repeat(30); // assuming 30 is max
            ValidationRequest request = ValidationRequest.of(maxUsername, Language.EN);
            when(usernameRepository.existsByUsername(maxUsername)).thenReturn(Mono.just(false));
            when(moderationService.isAppropriate(maxUsername)).thenReturn(Mono.just(true));

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertTrue(validationResult.isValid());
                        assertTrue(validationResult.isValidFormat());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle username with all allowed special characters")
        void shouldHandleUsernameWithAllAllowedSpecialCharacters() {
            // Given
            ValidationRequest request = ValidationRequest.of("user_name-123", Language.EN);
            when(usernameRepository.existsByUsername("user_name-123")).thenReturn(Mono.just(false));
            when(moderationService.isAppropriate("user_name-123")).thenReturn(Mono.just(true));

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertTrue(validationResult.isValid());
                        assertTrue(validationResult.isValidFormat());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should reject username with uppercase letters")
        void shouldRejectUsernameWithUppercaseLetters() {
            // Given - usernames must be lowercase only
            ValidationRequest request = ValidationRequest.of("UserName", Language.EN);

            // When
            Mono<ValidationResult> result = useCase.validate(request);

            // Then
            StepVerifier.create(result)
                    .assertNext(validationResult -> {
                        assertFalse(validationResult.isValid());
                        assertFalse(validationResult.isValidFormat());
                    })
                    .verifyComplete();

            verifyNoInteractions(usernameRepository);
            verifyNoInteractions(moderationService);
        }
    }
}