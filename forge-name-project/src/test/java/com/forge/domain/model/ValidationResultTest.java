package com.forge.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ValidationResult Domain Model Tests")
class ValidationResultTest {

    @Nested
    @DisplayName("Constructor Validation Tests")
    class ConstructorValidationTests {

        @Test
        @DisplayName("Should throw IllegalArgumentException when username is null")
        void shouldThrowExceptionWhenUsernameIsNull() {
            // Given
            String username = null;
            List<String> reasons = new ArrayList<>();

            // When & Then
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ValidationResult(username, true, true, true, true, reasons, 0.98, Instant.now())
            );

            assertEquals("Username cannot be null or blank", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when username is blank")
        void shouldThrowExceptionWhenUsernameIsBlank() {
            // Given
            String username = "   ";
            List<String> reasons = new ArrayList<>();

            // When & Then
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ValidationResult(username, true, true, true, true, reasons, 0.98, Instant.now())
            );

            assertEquals("Username cannot be null or blank", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when username is empty")
        void shouldThrowExceptionWhenUsernameIsEmpty() {
            // Given
            String username = "";
            List<String> reasons = new ArrayList<>();

            // When & Then
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ValidationResult(username, true, true, true, true, reasons, 0.98, Instant.now())
            );

            assertEquals("Username cannot be null or blank", exception.getMessage());
        }

        @Test
        @DisplayName("Should initialize empty list when reasons is null")
        void shouldInitializeEmptyListWhenReasonsIsNull() {
            // Given
            String username = "testuser";
            List<String> reasons = null;

            // When
            ValidationResult result = new ValidationResult(
                username, true, true, true, true, reasons, 0.98, Instant.now()
            );

            // Then
            assertNotNull(result.reasons());
            assertTrue(result.reasons().isEmpty());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when confidence score is below 0.0")
        void shouldThrowExceptionWhenConfidenceScoreBelowZero() {
            // Given
            String username = "testuser";
            double invalidScore = -0.1;

            // When & Then
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ValidationResult(username, true, true, true, true, List.of(), invalidScore, Instant.now())
            );

            assertEquals("Confidence score must be between 0.0 and 1.0", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when confidence score is above 1.0")
        void shouldThrowExceptionWhenConfidenceScoreAboveOne() {
            // Given
            String username = "testuser";
            double invalidScore = 1.1;

            // When & Then
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ValidationResult(username, true, true, true, true, List.of(), invalidScore, Instant.now())
            );

            assertEquals("Confidence score must be between 0.0 and 1.0", exception.getMessage());
        }

        @Test
        @DisplayName("Should accept confidence score of exactly 0.0")
        void shouldAcceptConfidenceScoreOfZero() {
            // Given
            String username = "testuser";
            double score = 0.0;

            // When
            ValidationResult result = new ValidationResult(
                username, false, false, false, false, List.of("invalid"), score, Instant.now()
            );

            // Then
            assertEquals(0.0, result.confidenceScore());
        }

        @Test
        @DisplayName("Should accept confidence score of exactly 1.0")
        void shouldAcceptConfidenceScoreOfOne() {
            // Given
            String username = "testuser";
            double score = 1.0;

            // When
            ValidationResult result = new ValidationResult(
                username, true, true, true, true, List.of(), score, Instant.now()
            );

            // Then
            assertEquals(1.0, result.confidenceScore());
        }

        @Test
        @DisplayName("Should set current timestamp when validatedAt is null")
        void shouldSetCurrentTimestampWhenValidatedAtIsNull() {
            // Given
            String username = "testuser";
            Instant before = Instant.now();

            // When
            ValidationResult result = new ValidationResult(
                username, true, true, true, true, List.of(), 0.98, null
            );

            Instant after = Instant.now();

            // Then
            assertNotNull(result.validatedAt());
            assertTrue(!result.validatedAt().isBefore(before));
            assertTrue(!result.validatedAt().isAfter(after));
        }

        @Test
        @DisplayName("Should create immutable copy of reasons list")
        void shouldCreateImmutableCopyOfReasonsList() {
            // Given
            String username = "testuser";
            List<String> mutableReasons = new ArrayList<>();
            mutableReasons.add("reason1");

            // When
            ValidationResult result = new ValidationResult(
                username, true, true, true, true, mutableReasons, 0.98, Instant.now()
            );

            // Modify original list
            mutableReasons.add("reason2");

            // Then
            assertEquals(1, result.reasons().size());
            assertThrows(UnsupportedOperationException.class, () -> result.reasons().add("reason3"));
        }
    }

    @Nested
    @DisplayName("Valid Factory Method Tests")
    class ValidFactoryMethodTests {

        @Test
        @DisplayName("Should create valid ValidationResult with all positive flags")
        void shouldCreateValidValidationResultWithAllPositiveFlags() {
            // Given
            String username = "validuser";
            Instant timestamp = Instant.now();

            // When
            ValidationResult result = ValidationResult.valid(username, timestamp);

            // Then
            assertEquals(username, result.username());
            assertTrue(result.isValid());
            assertTrue(result.isUnique());
            assertTrue(result.isAppropriate());
            assertTrue(result.isValidFormat());
            assertTrue(result.reasons().isEmpty());
            assertEquals(0.98, result.confidenceScore());
            assertEquals(timestamp, result.validatedAt());
        }

        @Test
        @DisplayName("Should create valid result with empty reasons list")
        void shouldCreateValidResultWithEmptyReasonsList() {
            // Given
            String username = "testuser";

            // When
            ValidationResult result = ValidationResult.valid(username, Instant.now());

            // Then
            assertNotNull(result.reasons());
            assertTrue(result.reasons().isEmpty());
        }

        @Test
        @DisplayName("Should set confidence score to 0.98 for valid usernames")
        void shouldSetConfidenceScoreToNinetyEightForValidUsernames() {
            // Given
            String username = "perfectuser";

            // When
            ValidationResult result = ValidationResult.valid(username, Instant.now());

            // Then
            assertEquals(0.98, result.confidenceScore());
        }
    }

    @Nested
    @DisplayName("Invalid Factory Method Tests")
    class InvalidFactoryMethodTests {

        @Test
        @DisplayName("Should create invalid result when all checks fail")
        void shouldCreateInvalidResultWhenAllChecksFail() {
            // Given
            String username = "baduser";
            List<String> reasons = List.of("Not unique", "Inappropriate content", "Invalid format");
            Instant timestamp = Instant.now();

            // When
            ValidationResult result = ValidationResult.invalid(
                username, false, false, false, reasons, timestamp
            );

            // Then
            assertEquals(username, result.username());
            assertFalse(result.isValid());
            assertFalse(result.isUnique());
            assertFalse(result.isAppropriate());
            assertFalse(result.isValidFormat());
            assertEquals(3, result.reasons().size());
            assertEquals(0.10, result.confidenceScore());
            assertEquals(timestamp, result.validatedAt());
        }

        @Test
        @DisplayName("Should calculate isValid as false when isUnique is false")
        void shouldCalculateIsValidAsFalseWhenIsUniqueIsFalse() {
            // Given
            String username = "duplicateuser";

            // When
            ValidationResult result = ValidationResult.invalid(
                username, false, true, true, List.of("Username already exists"), Instant.now()
            );

            // Then
            assertFalse(result.isValid());
            assertFalse(result.isUnique());
        }

        @Test
        @DisplayName("Should calculate isValid as false when isAppropriate is false")
        void shouldCalculateIsValidAsFalseWhenIsAppropriateIsFalse() {
            // Given
            String username = "inappropriateuser";

            // When
            ValidationResult result = ValidationResult.invalid(
                username, true, false, true, List.of("Contains offensive content"), Instant.now()
            );

            // Then
            assertFalse(result.isValid());
            assertFalse(result.isAppropriate());
        }

        @Test
        @DisplayName("Should calculate isValid as false when isValidFormat is false")
        void shouldCalculateIsValidAsFalseWhenIsValidFormatIsFalse() {
            // Given
            String username = "invalid@format";

            // When
            ValidationResult result = ValidationResult.invalid(
                username, true, true, false, List.of("Invalid characters"), Instant.now()
            );

            // Then
            assertFalse(result.isValid());
            assertFalse(result.isValidFormat());
        }

        @Test
        @DisplayName("Should calculate isValid as true when all checks pass")
        void shouldCalculateIsValidAsTrueWhenAllChecksPass() {
            // Given
            String username = "validuser";

            // When
            ValidationResult result = ValidationResult.invalid(
                username, true, true, true, List.of(), Instant.now()
            );

            // Then
            assertTrue(result.isValid());
        }
    }

    @Nested
    @DisplayName("Confidence Score Calculation Tests")
    class ConfidenceScoreCalculationTests {

        @Test
        @DisplayName("Should calculate confidence score 0.98 when all 3 checks pass")
        void shouldCalculateConfidenceScoreNinetyEightWhenAllChecksPass() {
            // Given
            String username = "perfectuser";

            // When
            ValidationResult result = ValidationResult.invalid(
                username, true, true, true, List.of(), Instant.now()
            );

            // Then
            assertEquals(0.98, result.confidenceScore());
        }

        @Test
        @DisplayName("Should calculate confidence score 0.85 when 2 checks pass")
        void shouldCalculateConfidenceScoreEightyFiveWhenTwoChecksPass() {
            // Given
            String username = "almostgood";

            // When - unique=true, appropriate=true, format=false
            ValidationResult result1 = ValidationResult.invalid(
                username, true, true, false, List.of("Invalid format"), Instant.now()
            );

            // Then
            assertEquals(0.85, result1.confidenceScore());

            // When - unique=true, appropriate=false, format=true
            ValidationResult result2 = ValidationResult.invalid(
                username, true, false, true, List.of("Inappropriate"), Instant.now()
            );

            // Then
            assertEquals(0.85, result2.confidenceScore());

            // When - unique=false, appropriate=true, format=true
            ValidationResult result3 = ValidationResult.invalid(
                username, false, true, true, List.of("Not unique"), Instant.now()
            );

            // Then
            assertEquals(0.85, result3.confidenceScore());
        }

        @Test
        @DisplayName("Should calculate confidence score 0.50 when only 1 check passes")
        void shouldCalculateConfidenceScoreFiftyWhenOneCheckPasses() {
            // Given
            String username = "barelyokay";

            // When - only unique=true
            ValidationResult result1 = ValidationResult.invalid(
                username, true, false, false, List.of("Inappropriate", "Invalid format"), Instant.now()
            );

            // Then
            assertEquals(0.50, result1.confidenceScore());

            // When - only appropriate=true
            ValidationResult result2 = ValidationResult.invalid(
                username, false, true, false, List.of("Not unique", "Invalid format"), Instant.now()
            );

            // Then
            assertEquals(0.50, result2.confidenceScore());

            // When - only format=true
            ValidationResult result3 = ValidationResult.invalid(
                username, false, false, true, List.of("Not unique", "Inappropriate"), Instant.now()
            );

            // Then
            assertEquals(0.50, result3.confidenceScore());
        }

        @Test
        @DisplayName("Should calculate confidence score 0.10 when no checks pass")
        void shouldCalculateConfidenceScoreTenWhenNoChecksPass() {
            // Given
            String username = "terribleuser";

            // When
            ValidationResult result = ValidationResult.invalid(
                username, false, false, false,
                List.of("Not unique", "Inappropriate", "Invalid format"),
                Instant.now()
            );

            // Then
            assertEquals(0.10, result.confidenceScore());
        }
    }

    @Nested
    @DisplayName("Record Functionality Tests")
    class RecordFunctionalityTests {

        @Test
        @DisplayName("Should maintain equality for records with same values")
        void shouldMaintainEqualityForRecordsWithSameValues() {
            // Given
            String username = "testuser";
            Instant timestamp = Instant.now();
            List<String> reasons = List.of("reason1");

            // When
            ValidationResult result1 = new ValidationResult(
                username, true, true, true, true, reasons, 0.98, timestamp
            );
            ValidationResult result2 = new ValidationResult(
                username, true, true, true, true, reasons, 0.98, timestamp
            );

            // Then
            assertEquals(result1, result2);
            assertEquals(result1.hashCode(), result2.hashCode());
        }

        @Test
        @DisplayName("Should maintain inequality for records with different values")
        void shouldMaintainInequalityForRecordsWithDifferentValues() {
            // Given
            Instant timestamp = Instant.now();

            // When
            ValidationResult result1 = ValidationResult.valid("user1", timestamp);
            ValidationResult result2 = ValidationResult.valid("user2", timestamp);

            // Then
            assertNotEquals(result1, result2);
        }

        @Test
        @DisplayName("Should generate meaningful toString representation")
        void shouldGenerateMeaningfulToStringRepresentation() {
            // Given
            String username = "testuser";
            Instant timestamp = Instant.now();

            // When
            ValidationResult result = ValidationResult.valid(username, timestamp);
            String toString = result.toString();

            // Then
            assertNotNull(toString);
            assertTrue(toString.contains("testuser"));
            assertTrue(toString.contains("true"));
            assertTrue(toString.contains("0.98"));
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Tests")
    class EdgeCasesAndBoundaryTests {

        @Test
        @DisplayName("Should handle username with special characters")
        void shouldHandleUsernameWithSpecialCharacters() {
            // Given
            String username = "user_123-test";

            // When
            ValidationResult result = ValidationResult.valid(username, Instant.now());

            // Then
            assertEquals(username, result.username());
        }

        @Test
        @DisplayName("Should handle very long username")
        void shouldHandleVeryLongUsername() {
            // Given
            String username = "a".repeat(255);

            // When
            ValidationResult result = ValidationResult.valid(username, Instant.now());

            // Then
            assertEquals(username, result.username());
            assertEquals(255, result.username().length());
        }

        @Test
        @DisplayName("Should handle empty reasons list correctly")
        void shouldHandleEmptyReasonsListCorrectly() {
            // Given
            String username = "testuser";
            List<String> emptyReasons = List.of();

            // When
            ValidationResult result = new ValidationResult(
                username, true, true, true, true, emptyReasons, 0.98, Instant.now()
            );

            // Then
            assertNotNull(result.reasons());
            assertTrue(result.reasons().isEmpty());
        }

        @Test
        @DisplayName("Should handle multiple reasons correctly")
        void shouldHandleMultipleReasonsCorrectly() {
            // Given
            String username = "baduser";
            List<String> reasons = List.of(
                "Username already exists",
                "Contains offensive language",
                "Invalid format: contains special characters",
                "Length exceeds maximum allowed"
            );

            // When
            ValidationResult result = ValidationResult.invalid(
                username, false, false, false, reasons, Instant.now()
            );

            // Then
            assertEquals(4, result.reasons().size());
            assertEquals("Username already exists", result.reasons().get(0));
            assertEquals("Length exceeds maximum allowed", result.reasons().get(3));
        }

        @Test
        @DisplayName("Should handle past timestamp correctly")
        void shouldHandlePastTimestampCorrectly() {
            // Given
            String username = "testuser";
            Instant pastTime = Instant.now().minusSeconds(3600);

            // When
            ValidationResult result = ValidationResult.valid(username, pastTime);

            // Then
            assertEquals(pastTime, result.validatedAt());
        }

        @Test
        @DisplayName("Should handle future timestamp correctly")
        void shouldHandleFutureTimestampCorrectly() {
            // Given
            String username = "testuser";
            Instant futureTime = Instant.now().plusSeconds(3600);

            // When
            ValidationResult result = ValidationResult.valid(username, futureTime);

            // Then
            assertEquals(futureTime, result.validatedAt());
        }
    }
}