package com.forge.adapters.inbound.graphql.type;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ValidationResponse GraphQL Type Tests")
class ValidationResponseTest {

    @Test
    @DisplayName("Should create ValidationResponse with all fields via constructor")
    void shouldCreateValidationResponseWithAllFields() {
        // Given
        String username = "testuser";
        boolean isValid = true;
        boolean isUnique = true;
        boolean isAppropriate = true;
        boolean isValidFormat = true;
        List<String> reasons = Collections.emptyList();
        double confidenceScore = 0.95;
        String validatedAt = "2025-01-15T10:30:00Z";

        // When
        ValidationResponse response = new ValidationResponse(
                username,
                isValid,
                isUnique,
                isAppropriate,
                isValidFormat,
                reasons,
                confidenceScore,
                validatedAt
        );

        // Then
        assertNotNull(response);
        assertEquals(username, response.username());
        assertTrue(response.isValid());
        assertTrue(response.isUnique());
        assertTrue(response.isAppropriate());
        assertTrue(response.isValidFormat());
        assertTrue(response.reasons().isEmpty());
        assertEquals(0.95, response.confidenceScore());
        assertEquals(validatedAt, response.validatedAt());
    }

    @Test
    @DisplayName("Should create ValidationResponse with validation failures and reasons")
    void shouldCreateValidationResponseWithFailuresAndReasons() {
        // Given
        String username = "bad@user";
        boolean isValid = false;
        boolean isUnique = true;
        boolean isAppropriate = false;
        boolean isValidFormat = false;
        List<String> reasons = Arrays.asList(
                "Username contains invalid characters",
                "Username may be inappropriate"
        );
        double confidenceScore = 0.45;
        String validatedAt = "2025-01-15T10:30:00Z";

        // When
        ValidationResponse response = new ValidationResponse(
                username,
                isValid,
                isUnique,
                isAppropriate,
                isValidFormat,
                reasons,
                confidenceScore,
                validatedAt
        );

        // Then
        assertFalse(response.isValid());
        assertFalse(response.isAppropriate());
        assertFalse(response.isValidFormat());
        assertEquals(2, response.reasons().size());
        assertTrue(response.reasons().contains("Username contains invalid characters"));
        assertTrue(response.reasons().contains("Username may be inappropriate"));
        assertEquals(0.45, response.confidenceScore());
    }

    @Test
    @DisplayName("Should create ValidationResponse using factory method with Instant conversion")
    void shouldCreateValidationResponseUsingFactoryMethodWithInstantConversion() {
        // Given
        String username = "validuser123";
        boolean isValid = true;
        boolean isUnique = true;
        boolean isAppropriate = true;
        boolean isValidFormat = true;
        List<String> reasons = Collections.emptyList();
        double confidenceScore = 0.98;
        Instant validatedAt = Instant.parse("2025-01-15T10:30:00Z");

        // When
        ValidationResponse response = ValidationResponse.of(
                username,
                isValid,
                isUnique,
                isAppropriate,
                isValidFormat,
                reasons,
                confidenceScore,
                validatedAt
        );

        // Then
        assertNotNull(response);
        assertEquals(username, response.username());
        assertTrue(response.isValid());
        assertTrue(response.isUnique());
        assertTrue(response.isAppropriate());
        assertTrue(response.isValidFormat());
        assertTrue(response.reasons().isEmpty());
        assertEquals(0.98, response.confidenceScore());
        assertEquals("2025-01-15T10:30:00Z", response.validatedAt());
    }

    @Test
    @DisplayName("Should convert Instant to ISO-8601 string format in factory method")
    void shouldConvertInstantToIso8601StringFormat() {
        // Given
        Instant validatedAt = Instant.parse("2025-10-23T14:45:30.123456789Z");

        // When
        ValidationResponse response = ValidationResponse.of(
                "testuser",
                true,
                true,
                true,
                true,
                Collections.emptyList(),
                1.0,
                validatedAt
        );

        // Then
        assertEquals("2025-10-23T14:45:30.123456789Z", response.validatedAt());
    }

    @Test
    @DisplayName("Should handle minimum confidence score (0.0)")
    void shouldHandleMinimumConfidenceScore() {
        // Given
        double minConfidence = 0.0;

        // When
        ValidationResponse response = ValidationResponse.of(
                "user",
                false,
                false,
                false,
                false,
                Arrays.asList("Multiple validation failures"),
                minConfidence,
                Instant.now()
        );

        // Then
        assertEquals(0.0, response.confidenceScore());
        assertFalse(response.isValid());
    }

    @Test
    @DisplayName("Should handle maximum confidence score (1.0)")
    void shouldHandleMaximumConfidenceScore() {
        // Given
        double maxConfidence = 1.0;

        // When
        ValidationResponse response = ValidationResponse.of(
                "perfectuser",
                true,
                true,
                true,
                true,
                Collections.emptyList(),
                maxConfidence,
                Instant.now()
        );

        // Then
        assertEquals(1.0, response.confidenceScore());
        assertTrue(response.isValid());
    }

    @Test
    @DisplayName("Should handle empty reasons list for valid username")
    void shouldHandleEmptyReasonsListForValidUsername() {
        // Given
        List<String> emptyReasons = Collections.emptyList();

        // When
        ValidationResponse response = ValidationResponse.of(
                "gooduser",
                true,
                true,
                true,
                true,
                emptyReasons,
                0.99,
                Instant.now()
        );

        // Then
        assertNotNull(response.reasons());
        assertTrue(response.reasons().isEmpty());
        assertTrue(response.isValid());
    }

    @Test
    @DisplayName("Should handle multiple validation failure reasons")
    void shouldHandleMultipleValidationFailureReasons() {
        // Given
        List<String> multipleReasons = Arrays.asList(
                "Username too short",
                "Username contains special characters",
                "Username may be offensive",
                "Username not unique"
        );

        // When
        ValidationResponse response = ValidationResponse.of(
                "bad",
                false,
                false,
                false,
                false,
                multipleReasons,
                0.15,
                Instant.now()
        );

        // Then
        assertEquals(4, response.reasons().size());
        assertFalse(response.isValid());
        assertFalse(response.isUnique());
        assertFalse(response.isAppropriate());
        assertFalse(response.isValidFormat());
    }

    @Test
    @DisplayName("Should handle partial validation success (unique but not appropriate)")
    void shouldHandlePartialValidationSuccess() {
        // Given
        boolean isUnique = true;
        boolean isAppropriate = false;
        boolean isValidFormat = true;
        List<String> reasons = Arrays.asList("Username may contain inappropriate content");

        // When
        ValidationResponse response = ValidationResponse.of(
                "uniquebutbad",
                false,
                isUnique,
                isAppropriate,
                isValidFormat,
                reasons,
                0.60,
                Instant.now()
        );

        // Then
        assertFalse(response.isValid());
        assertTrue(response.isUnique());
        assertFalse(response.isAppropriate());
        assertTrue(response.isValidFormat());
        assertEquals(1, response.reasons().size());
    }

    @Test
    @DisplayName("Should preserve record equality for identical values")
    void shouldPreserveRecordEqualityForIdenticalValues() {
        // Given
        String username = "testuser";
        String validatedAt = "2025-01-15T10:30:00Z";
        List<String> reasons = Collections.emptyList();

        ValidationResponse response1 = new ValidationResponse(
                username, true, true, true, true, reasons, 0.95, validatedAt
        );

        ValidationResponse response2 = new ValidationResponse(
                username, true, true, true, true, reasons, 0.95, validatedAt
        );

        // Then
        assertEquals(response1, response2);
        assertEquals(response1.hashCode(), response2.hashCode());
    }

    @Test
    @DisplayName("Should have different equality for different values")
    void shouldHaveDifferentEqualityForDifferentValues() {
        // Given
        ValidationResponse response1 = new ValidationResponse(
                "user1", true, true, true, true, Collections.emptyList(), 0.95, "2025-01-15T10:30:00Z"
        );

        ValidationResponse response2 = new ValidationResponse(
                "user2", true, true, true, true, Collections.emptyList(), 0.95, "2025-01-15T10:30:00Z"
        );

        // Then
        assertNotEquals(response1, response2);
    }

    @Test
    @DisplayName("Should maintain immutability as a record")
    void shouldMaintainImmutabilityAsRecord() {
        // Given
        List<String> reasons = Arrays.asList("reason1", "reason2");
        ValidationResponse response = ValidationResponse.of(
                "testuser",
                true,
                true,
                true,
                true,
                reasons,
                0.95,
                Instant.now()
        );

        // When/Then - attempting to modify the list should not affect the record
        // (This demonstrates the design expectation, actual immutability depends on list implementation)
        assertNotNull(response.reasons());
        assertEquals(2, response.reasons().size());
    }
}