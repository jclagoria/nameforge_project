package com.forge.adapters.inbound.graphql.mapper;

import com.forge.adapters.inbound.graphql.type.ValidationResponse;
import com.forge.domain.model.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GraphQL Validation Mapper Tests")
class GraphQLValidationMapperTest {

    private GraphQLValidationMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new GraphQLValidationMapper();
    }

    @Test
    @DisplayName("Should map valid ValidationResult to GraphQL ValidationResponse")
    void shouldMapValidValidationResultToGraphQLResponse() {
        // Given
        Instant validatedAt = Instant.parse("2025-01-15T10:30:00Z");
        ValidationResult domainResult = ValidationResult.valid("validuser123", validatedAt);

        // When
        ValidationResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // Then
        assertNotNull(graphqlResponse);
        assertEquals("validuser123", graphqlResponse.username());
        assertTrue(graphqlResponse.isValid());
        assertTrue(graphqlResponse.isUnique());
        assertTrue(graphqlResponse.isAppropriate());
        assertTrue(graphqlResponse.isValidFormat());
        assertTrue(graphqlResponse.reasons().isEmpty());
        assertEquals(0.98, graphqlResponse.confidenceScore());
        assertEquals("2025-01-15T10:30:00Z", graphqlResponse.validatedAt());
    }

    @Test
    @DisplayName("Should map invalid ValidationResult with all failures to GraphQL response")
    void shouldMapInvalidValidationResultWithAllFailures() {
        // Given
        Instant validatedAt = Instant.parse("2025-01-15T10:30:00Z");
        List<String> reasons = Arrays.asList(
                "Username contains invalid characters",
                "Username is not unique",
                "Username may be inappropriate"
        );
        ValidationResult domainResult = ValidationResult.invalid(
                "bad@user",
                false,
                false,
                false,
                reasons,
                validatedAt
        );

        // When
        ValidationResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // Then
        assertNotNull(graphqlResponse);
        assertEquals("bad@user", graphqlResponse.username());
        assertFalse(graphqlResponse.isValid());
        assertFalse(graphqlResponse.isUnique());
        assertFalse(graphqlResponse.isAppropriate());
        assertFalse(graphqlResponse.isValidFormat());
        assertEquals(3, graphqlResponse.reasons().size());
        assertEquals(0.10, graphqlResponse.confidenceScore());
        assertEquals("2025-01-15T10:30:00Z", graphqlResponse.validatedAt());
    }

    @Test
    @DisplayName("Should map ValidationResult with partial validation failures")
    void shouldMapValidationResultWithPartialFailures() {
        // Given
        Instant validatedAt = Instant.parse("2025-01-15T10:30:00Z");
        List<String> reasons = Arrays.asList("Username may contain inappropriate content");
        ValidationResult domainResult = ValidationResult.invalid(
                "uniquebutbad",
                true,
                false,
                true,
                reasons,
                validatedAt
        );

        // When
        ValidationResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // Then
        assertNotNull(graphqlResponse);
        assertEquals("uniquebutbad", graphqlResponse.username());
        assertFalse(graphqlResponse.isValid()); // Overall validation fails
        assertTrue(graphqlResponse.isUnique());
        assertFalse(graphqlResponse.isAppropriate());
        assertTrue(graphqlResponse.isValidFormat());
        assertEquals(1, graphqlResponse.reasons().size());
        assertTrue(graphqlResponse.reasons().contains("Username may contain inappropriate content"));
        assertEquals(0.85, graphqlResponse.confidenceScore()); // 2 out of 3 checks passed
    }

    @Test
    @DisplayName("Should correctly convert Instant to ISO-8601 string in mapped response")
    void shouldCorrectlyConvertInstantToIso8601String() {
        // Given
        Instant validatedAt = Instant.parse("2025-10-23T14:45:30.123456789Z");
        ValidationResult domainResult = ValidationResult.valid("testuser", validatedAt);

        // When
        ValidationResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // Then
        assertEquals("2025-10-23T14:45:30.123456789Z", graphqlResponse.validatedAt());
    }

    @Test
    @DisplayName("Should map empty reasons list for valid username")
    void shouldMapEmptyReasonsListForValidUsername() {
        // Given
        Instant validatedAt = Instant.now();
        ValidationResult domainResult = ValidationResult.valid("gooduser", validatedAt);

        // When
        ValidationResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // Then
        assertNotNull(graphqlResponse.reasons());
        assertTrue(graphqlResponse.reasons().isEmpty());
    }

    @Test
    @DisplayName("Should map multiple validation failure reasons correctly")
    void shouldMapMultipleValidationFailureReasons() {
        // Given
        Instant validatedAt = Instant.now();
        List<String> reasons = Arrays.asList(
                "Username too short",
                "Username contains special characters",
                "Username may be offensive",
                "Username not unique"
        );
        ValidationResult domainResult = ValidationResult.invalid(
                "bad",
                false,
                false,
                false,
                reasons,
                validatedAt
        );

        // When
        ValidationResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // Then
        assertEquals(4, graphqlResponse.reasons().size());
        assertTrue(graphqlResponse.reasons().contains("Username too short"));
        assertTrue(graphqlResponse.reasons().contains("Username contains special characters"));
        assertTrue(graphqlResponse.reasons().contains("Username may be offensive"));
        assertTrue(graphqlResponse.reasons().contains("Username not unique"));
    }

    @Test
    @DisplayName("Should preserve confidence score during mapping")
    void shouldPreserveConfidenceScoreDuringMapping() {
        // Given
        Instant validatedAt = Instant.now();

        // Test with high confidence (all checks pass)
        ValidationResult highConfidence = ValidationResult.valid("perfectuser", validatedAt);

        // When
        ValidationResponse highResponse = mapper.toGraphQL(highConfidence);

        // Then
        assertEquals(0.98, highResponse.confidenceScore());
    }

    @Test
    @DisplayName("Should map confidence score for one passing check")
    void shouldMapConfidenceScoreForOnePassingCheck() {
        // Given
        Instant validatedAt = Instant.now();
        List<String> reasons = Arrays.asList("Not unique", "Inappropriate content");
        ValidationResult domainResult = ValidationResult.invalid(
                "testuser",
                false,
                false,
                true, // Only format is valid
                reasons,
                validatedAt
        );

        // When
        ValidationResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // Then
        assertEquals(0.50, graphqlResponse.confidenceScore());
    }

    @Test
    @DisplayName("Should map all boolean validation flags correctly")
    void shouldMapAllBooleanValidationFlagsCorrectly() {
        // Given
        Instant validatedAt = Instant.now();
        ValidationResult domainResult = new ValidationResult(
                "testuser",
                false,
                true,  // isUnique
                false, // isAppropriate
                true,  // isValidFormat
                Arrays.asList("Inappropriate content detected"),
                0.75,
                validatedAt
        );

        // When
        ValidationResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // Then
        assertFalse(graphqlResponse.isValid());
        assertTrue(graphqlResponse.isUnique());
        assertFalse(graphqlResponse.isAppropriate());
        assertTrue(graphqlResponse.isValidFormat());
    }

    @Test
    @DisplayName("Should map username field correctly")
    void shouldMapUsernameFieldCorrectly() {
        // Given
        String username = "unique_username_123";
        Instant validatedAt = Instant.now();
        ValidationResult domainResult = ValidationResult.valid(username, validatedAt);

        // When
        ValidationResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // Then
        assertEquals(username, graphqlResponse.username());
    }

    @Test
    @DisplayName("Should maintain data integrity during domain to GraphQL conversion")
    void shouldMaintainDataIntegrityDuringConversion() {
        // Given
        String username = "testuser456";
        boolean isValid = true;
        boolean isUnique = true;
        boolean isAppropriate = true;
        boolean isValidFormat = true;
        List<String> reasons = Collections.emptyList();
        double confidenceScore = 0.98;
        Instant validatedAt = Instant.parse("2025-01-15T10:30:00Z");

        ValidationResult domainResult = new ValidationResult(
                username,
                isValid,
                isUnique,
                isAppropriate,
                isValidFormat,
                reasons,
                confidenceScore,
                validatedAt
        );

        // When
        ValidationResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // Then - verify all fields are mapped correctly
        assertEquals(username, graphqlResponse.username());
        assertEquals(isValid, graphqlResponse.isValid());
        assertEquals(isUnique, graphqlResponse.isUnique());
        assertEquals(isAppropriate, graphqlResponse.isAppropriate());
        assertEquals(isValidFormat, graphqlResponse.isValidFormat());
        assertEquals(reasons.size(), graphqlResponse.reasons().size());
        assertEquals(confidenceScore, graphqlResponse.confidenceScore());
        assertEquals(validatedAt.toString(), graphqlResponse.validatedAt());
    }

    @Test
    @DisplayName("Should handle edge case with current timestamp")
    void shouldHandleEdgeCaseWithCurrentTimestamp() {
        // Given
        Instant now = Instant.now();
        ValidationResult domainResult = ValidationResult.valid("user", now);

        // When
        ValidationResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // Then
        assertNotNull(graphqlResponse.validatedAt());
        assertEquals(now.toString(), graphqlResponse.validatedAt());
    }
}