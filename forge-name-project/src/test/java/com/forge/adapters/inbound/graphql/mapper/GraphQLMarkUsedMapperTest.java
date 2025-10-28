package com.forge.adapters.inbound.graphql.mapper;

import com.forge.adapters.inbound.graphql.type.MarkUsedResponse;
import com.forge.domain.model.MarkUsedResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GraphQL Mark Used Mapper Tests")
class GraphQLMarkUsedMapperTest {

    private GraphQLMarkUsedMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new GraphQLMarkUsedMapper();
    }

    @Test
    @DisplayName("Should map successfully marked username to GraphQL response")
    void shouldMapSuccessfullyMarkedUsernameToGraphQLResponse() {
        // ARRANGE
        String username = "cleverpanda42";
        LocalDateTime markedAt = LocalDateTime.of(2025, 10, 28, 14, 30, 0);
        MarkUsedResult domainResult = MarkUsedResult.marked(username, markedAt);

        // ACT
        MarkUsedResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // ASSERT
        assertNotNull(graphqlResponse, "GraphQL response should not be null");
        assertEquals(username, graphqlResponse.username(), "Username should match");
        assertTrue(graphqlResponse.marked(), "marked flag should be true for newly marked username");
        assertFalse(graphqlResponse.wasAlreadyUsed(), "wasAlreadyUsed should be false for first-time marking");
        assertNotNull(graphqlResponse.markedAt(), "markedAt timestamp should not be null");
        assertEquals(markedAt.toInstant(ZoneOffset.UTC), graphqlResponse.markedAt(),
                "markedAt should be converted to UTC Instant");
        assertEquals("Username successfully marked as used", graphqlResponse.message(),
                "Success message should be present");
    }

    @Test
    @DisplayName("Should map already used username to GraphQL response")
    void shouldMapAlreadyUsedUsernameToGraphQLResponse() {
        // ARRANGE
        String username = "existinguser123";
        MarkUsedResult domainResult = MarkUsedResult.alreadyUsed(username);

        // ACT
        MarkUsedResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // ASSERT
        assertNotNull(graphqlResponse, "GraphQL response should not be null");
        assertEquals(username, graphqlResponse.username(), "Username should match");
        assertFalse(graphqlResponse.marked(), "marked flag should be false when already used");
        assertTrue(graphqlResponse.wasAlreadyUsed(), "wasAlreadyUsed should be true for duplicate marking");
        assertNull(graphqlResponse.markedAt(), "markedAt should be null for already used username");
        assertEquals("Username was already used", graphqlResponse.message(),
                "Already used message should be present");
    }

    @Test
    @DisplayName("Should correctly convert LocalDateTime to UTC Instant")
    void shouldCorrectlyConvertLocalDateTimeToUtcInstant() {
        // ARRANGE
        LocalDateTime localDateTime = LocalDateTime.of(2025, 1, 15, 10, 30, 45, 123456789);
        MarkUsedResult domainResult = MarkUsedResult.marked("testuser", localDateTime);

        // ACT
        MarkUsedResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // ASSERT
        Instant expectedInstant = localDateTime.toInstant(ZoneOffset.UTC);
        assertEquals(expectedInstant, graphqlResponse.markedAt(),
                "LocalDateTime should be converted to UTC Instant");
        assertEquals("2025-01-15T10:30:45.123456789Z", graphqlResponse.markedAt().toString(),
                "Instant should be in ISO-8601 format with nanosecond precision");
    }

    @Test
    @DisplayName("Should handle marked flag logic correctly - not already used")
    void shouldHandleMarkedFlagLogicForNewUsername() {
        // ARRANGE
        MarkUsedResult domainResult = MarkUsedResult.marked("newuser", LocalDateTime.now());

        // ACT
        MarkUsedResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // ASSERT
        assertTrue(graphqlResponse.marked(), "marked should be true (not wasAlreadyUsed)");
        assertFalse(graphqlResponse.wasAlreadyUsed(), "wasAlreadyUsed should be false");
        assertEquals("Username successfully marked as used", graphqlResponse.message());
    }

    @Test
    @DisplayName("Should handle marked flag logic correctly - already used")
    void shouldHandleMarkedFlagLogicForExistingUsername() {
        // ARRANGE
        MarkUsedResult domainResult = MarkUsedResult.alreadyUsed("existinguser");

        // ACT
        MarkUsedResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // ASSERT
        assertFalse(graphqlResponse.marked(), "marked should be false (wasAlreadyUsed)");
        assertTrue(graphqlResponse.wasAlreadyUsed(), "wasAlreadyUsed should be true");
        assertEquals("Username was already used", graphqlResponse.message());
    }

    @Test
    @DisplayName("Should preserve username exactly as provided")
    void shouldPreserveUsernameExactly() {
        // ARRANGE
        String username = "complex_username-123";
        MarkUsedResult domainResult = MarkUsedResult.marked(username, LocalDateTime.now());

        // ACT
        MarkUsedResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // ASSERT
        assertEquals(username, graphqlResponse.username(),
                "Username should be preserved exactly without modification");
    }

    @Test
    @DisplayName("Should handle edge case with current timestamp")
    void shouldHandleEdgeCaseWithCurrentTimestamp() {
        // ARRANGE
        LocalDateTime now = LocalDateTime.now();
        MarkUsedResult domainResult = MarkUsedResult.marked("user", now);

        // ACT
        MarkUsedResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // ASSERT
        assertNotNull(graphqlResponse.markedAt(), "Current timestamp should be mapped");
        assertEquals(now.toInstant(ZoneOffset.UTC), graphqlResponse.markedAt(),
                "Current timestamp should be converted to UTC Instant");
    }

    @Test
    @DisplayName("Should handle midnight timestamp correctly")
    void shouldHandleMidnightTimestampCorrectly() {
        // ARRANGE
        LocalDateTime midnight = LocalDateTime.of(2025, 1, 1, 0, 0, 0);
        MarkUsedResult domainResult = MarkUsedResult.marked("midnightuser", midnight);

        // ACT
        MarkUsedResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // ASSERT
        assertEquals(Instant.parse("2025-01-01T00:00:00Z"), graphqlResponse.markedAt(),
                "Midnight timestamp should be correctly converted");
    }

    @Test
    @DisplayName("Should handle end of day timestamp correctly")
    void shouldHandleEndOfDayTimestampCorrectly() {
        // ARRANGE
        LocalDateTime endOfDay = LocalDateTime.of(2025, 12, 31, 23, 59, 59, 999999999);
        MarkUsedResult domainResult = MarkUsedResult.marked("eoduser", endOfDay);

        // ACT
        MarkUsedResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // ASSERT
        assertEquals(Instant.parse("2025-12-31T23:59:59.999999999Z"), graphqlResponse.markedAt(),
                "End of day timestamp should preserve nanosecond precision");
    }

    @Test
    @DisplayName("Should generate correct success message")
    void shouldGenerateCorrectSuccessMessage() {
        // ARRANGE
        MarkUsedResult domainResult = MarkUsedResult.marked("successuser", LocalDateTime.now());

        // ACT
        MarkUsedResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // ASSERT
        assertEquals("Username successfully marked as used", graphqlResponse.message(),
                "Success message should follow standard format");
    }

    @Test
    @DisplayName("Should generate correct already used message")
    void shouldGenerateCorrectAlreadyUsedMessage() {
        // ARRANGE
        MarkUsedResult domainResult = MarkUsedResult.alreadyUsed("useduser");

        // ACT
        MarkUsedResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // ASSERT
        assertEquals("Username was already used", graphqlResponse.message(),
                "Already used message should follow standard format");
    }

    @Test
    @DisplayName("Should maintain data integrity during domain to GraphQL conversion - success case")
    void shouldMaintainDataIntegrityForSuccessCase() {
        // ARRANGE
        String username = "dataintegrity_user";
        LocalDateTime markedAt = LocalDateTime.of(2025, 6, 15, 12, 0, 0);
        MarkUsedResult domainResult = MarkUsedResult.marked(username, markedAt);

        // ACT
        MarkUsedResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // ASSERT - verify all fields are correctly mapped
        assertEquals(username, graphqlResponse.username());
        assertTrue(graphqlResponse.marked());
        assertFalse(graphqlResponse.wasAlreadyUsed());
        assertEquals(markedAt.toInstant(ZoneOffset.UTC), graphqlResponse.markedAt());
        assertEquals("Username successfully marked as used", graphqlResponse.message());
    }

    @Test
    @DisplayName("Should maintain data integrity during domain to GraphQL conversion - already used case")
    void shouldMaintainDataIntegrityForAlreadyUsedCase() {
        // ARRANGE
        String username = "duplicate_user";
        MarkUsedResult domainResult = MarkUsedResult.alreadyUsed(username);

        // ACT
        MarkUsedResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // ASSERT - verify all fields are correctly mapped
        assertEquals(username, graphqlResponse.username());
        assertFalse(graphqlResponse.marked());
        assertTrue(graphqlResponse.wasAlreadyUsed());
        assertNull(graphqlResponse.markedAt());
        assertEquals("Username was already used", graphqlResponse.message());
    }

    @Test
    @DisplayName("Should handle username with minimum valid length")
    void shouldHandleUsernameWithMinimumValidLength() {
        // ARRANGE
        String minUsername = "abcde"; // 5 characters (minimum)
        MarkUsedResult domainResult = MarkUsedResult.marked(minUsername, LocalDateTime.now());

        // ACT
        MarkUsedResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // ASSERT
        assertEquals(minUsername, graphqlResponse.username());
        assertTrue(graphqlResponse.marked());
    }

    @Test
    @DisplayName("Should handle username with maximum valid length")
    void shouldHandleUsernameWithMaximumValidLength() {
        // ARRANGE
        String maxUsername = "a".repeat(30); // 30 characters (maximum)
        MarkUsedResult domainResult = MarkUsedResult.marked(maxUsername, LocalDateTime.now());

        // ACT
        MarkUsedResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // ASSERT
        assertEquals(maxUsername, graphqlResponse.username());
        assertEquals(30, graphqlResponse.username().length());
        assertTrue(graphqlResponse.marked());
    }

    @Test
    @DisplayName("Should handle username with special allowed characters")
    void shouldHandleUsernameWithSpecialAllowedCharacters() {
        // ARRANGE
        String specialUsername = "user_name-123";
        MarkUsedResult domainResult = MarkUsedResult.marked(specialUsername, LocalDateTime.now());

        // ACT
        MarkUsedResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // ASSERT
        assertEquals(specialUsername, graphqlResponse.username());
        assertTrue(graphqlResponse.marked());
    }

    @Test
    @DisplayName("Should preserve timestamp precision across conversion")
    void shouldPreserveTimestampPrecisionAcrossConversion() {
        // ARRANGE
        LocalDateTime preciseTime = LocalDateTime.of(2025, 3, 21, 15, 45, 30, 123456789);
        MarkUsedResult domainResult = MarkUsedResult.marked("preciseuser", preciseTime);

        // ACT
        MarkUsedResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // ASSERT
        Instant expectedInstant = preciseTime.toInstant(ZoneOffset.UTC);
        assertEquals(expectedInstant, graphqlResponse.markedAt(),
                "Nanosecond precision should be preserved");
        assertEquals(123456789, graphqlResponse.markedAt().getNano(),
                "Nanosecond component should match exactly");
    }

    @Test
    @DisplayName("Should handle leap year date correctly")
    void shouldHandleLeapYearDateCorrectly() {
        // ARRANGE
        LocalDateTime leapDay = LocalDateTime.of(2024, 2, 29, 12, 0, 0);
        MarkUsedResult domainResult = MarkUsedResult.marked("leapuser", leapDay);

        // ACT
        MarkUsedResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // ASSERT
        assertEquals(Instant.parse("2024-02-29T12:00:00Z"), graphqlResponse.markedAt(),
                "Leap year date should be handled correctly");
    }

    @Test
    @DisplayName("Should map GraphQL response structure correctly for REST to GraphQL cache sharing")
    void shouldMapForCrossPlatformCompatibility() {
        // ARRANGE - Simulate REST marking username, then GraphQL querying result
        String username = "cross_platform_user";
        LocalDateTime markedAt = LocalDateTime.of(2025, 10, 28, 16, 0, 0);
        MarkUsedResult domainResult = MarkUsedResult.marked(username, markedAt);

        // ACT
        MarkUsedResponse graphqlResponse = mapper.toGraphQL(domainResult);

        // ASSERT - Verify GraphQL response matches expected schema
        assertNotNull(graphqlResponse.username());
        assertNotNull(graphqlResponse.marked());
        assertNotNull(graphqlResponse.wasAlreadyUsed());
        assertNotNull(graphqlResponse.markedAt());
        assertNotNull(graphqlResponse.message());
    }
}
