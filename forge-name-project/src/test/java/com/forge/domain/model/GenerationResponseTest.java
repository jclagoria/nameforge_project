package com.forge.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GenerationResponse Tests")
class GenerationResponseTest {

    private Username createValidUsername(String value) {
        return Username.of(value, Language.EN, PatternType.CLASSIC);
    }

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("Should create with valid parameters")
        void shouldCreateWithValidParameters() {
            // Arrange
            List<Username> usernames = List.of(createValidUsername("user123"));
            Instant timestamp = Instant.now();
            Language language = Language.EN;

            // Act
            GenerationResponse response = new GenerationResponse(
                    usernames,
                    timestamp,
                    language,
                    1,
                    false,
                    100L
            );

            // Assert
            assertNotNull(response);
        }

        @Test
        @DisplayName("Should store usernames list")
        void shouldStoreUsernamesList() {
            // Arrange
            List<Username> expectedUsernames = List.of(
                    createValidUsername("user123"),
                    createValidUsername("user456")
            );

            // Act
            GenerationResponse response = new GenerationResponse(
                    expectedUsernames,
                    Instant.now(),
                    Language.EN,
                    2,
                    false,
                    100L
            );

            // Assert
            assertEquals(expectedUsernames, response.usernames());
        }

        @Test
        @DisplayName("Should store generated at timestamp")
        void shouldStoreGeneratedAtTimestamp() {
            // Arrange
            Instant expectedTimestamp = Instant.parse("2024-01-15T10:30:00Z");

            // Act
            GenerationResponse response = new GenerationResponse(
                    List.of(createValidUsername("user123")),
                    expectedTimestamp,
                    Language.EN,
                    1,
                    false,
                    100L
            );

            // Assert
            assertEquals(expectedTimestamp, response.generatedAt());
        }

        @Test
        @DisplayName("Should store language")
        void shouldStoreLanguage() {
            // Arrange
            Language expectedLanguage = Language.ES;

            // Act
            GenerationResponse response = new GenerationResponse(
                    List.of(createValidUsername("user123")),
                    Instant.now(),
                    expectedLanguage,
                    1,
                    false,
                    100L
            );

            // Assert
            assertEquals(expectedLanguage, response.language());
        }

        @Test
        @DisplayName("Should store total generated count")
        void shouldStoreTotalGeneratedCount() {
            // Arrange
            int expectedTotal = 3;

            // Act
            GenerationResponse response = new GenerationResponse(
                    List.of(createValidUsername("user123")),
                    Instant.now(),
                    Language.EN,
                    expectedTotal,
                    false,
                    100L
            );

            // Assert
            assertEquals(expectedTotal, response.totalGenerated());
        }

        @Test
        @DisplayName("Should store cache hit flag")
        void shouldStoreCacheHitFlag() {
            // Arrange
            boolean expectedCacheHit = true;

            // Act
            GenerationResponse response = new GenerationResponse(
                    List.of(createValidUsername("user123")),
                    Instant.now(),
                    Language.EN,
                    1,
                    expectedCacheHit,
                    100L
            );

            // Assert
            assertEquals(expectedCacheHit, response.cacheHit());
        }

        @Test
        @DisplayName("Should store response time")
        void shouldStoreResponseTime() {
            // Arrange
            long expectedResponseTime = 250L;

            // Act
            GenerationResponse response = new GenerationResponse(
                    List.of(createValidUsername("user123")),
                    Instant.now(),
                    Language.EN,
                    1,
                    false,
                    expectedResponseTime
            );

            // Assert
            assertEquals(expectedResponseTime, response.responseTimeMs());
        }
    }

    @Nested
    @DisplayName("Factory method")
    class FactoryMethod {

        @Test
        @DisplayName("Should create with factory method")
        void shouldCreateWithFactoryMethod() {
            // Arrange
            List<Username> usernames = List.of(createValidUsername("user123"));

            // Act
            GenerationResponse response = GenerationResponse.of(
                    usernames,
                    Language.EN,
                    false,
                    100L
            );

            // Assert
            assertNotNull(response);
        }

        @Test
        @DisplayName("Should set generated at automatically")
        void shouldSetGeneratedAtAutomatically() {
            // Arrange
            List<Username> usernames = List.of(createValidUsername("user123"));
            Instant before = Instant.now();

            // Act
            GenerationResponse response = GenerationResponse.of(
                    usernames,
                    Language.EN,
                    false,
                    100L
            );

            // Assert
            Instant after = Instant.now();
            assertTrue(
                    !response.generatedAt().isBefore(before) &&
                    !response.generatedAt().isAfter(after)
            );
        }

        @Test
        @DisplayName("Should set total generated from list size")
        void shouldSetTotalGeneratedFromListSize() {
            // Arrange
            List<Username> usernames = List.of(
                    createValidUsername("user123"),
                    createValidUsername("user456"),
                    createValidUsername("user789")
            );

            // Act
            GenerationResponse response = GenerationResponse.of(
                    usernames,
                    Language.EN,
                    false,
                    100L
            );

            // Assert
            assertEquals(3, response.totalGenerated());
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Nested
        @DisplayName("Language validation")
        class LanguageValidation {

            @Test
            @DisplayName("Should reject null language in constructor")
            void shouldRejectNullLanguageInConstructor() {
                // Act & Assert
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new GenerationResponse(
                                List.of(createValidUsername("user123")),
                                Instant.now(),
                                null,
                                1,
                                false,
                                100L
                        )
                );
            }

            @Test
            @DisplayName("Should reject null language in factory method")
            void shouldRejectNullLanguageInFactoryMethod() {
                // Act & Assert
                assertThrows(
                        IllegalArgumentException.class,
                        () -> GenerationResponse.of(
                                List.of(createValidUsername("user123")),
                                null,
                                false,
                                100L
                        )
                );
            }
        }

        @Nested
        @DisplayName("Usernames validation")
        class UsernamesValidation {

            @Test
            @DisplayName("Should reject null usernames in constructor")
            void shouldRejectNullUsernamesInConstructor() {
                // Act & Assert
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new GenerationResponse(
                                null,
                                Instant.now(),
                                Language.EN,
                                0,
                                false,
                                100L
                        )
                );
            }

            @Test
            @DisplayName("Should reject null usernames in factory method")
            void shouldRejectNullUsernamesInFactoryMethod() {
                // Act & Assert
                assertThrows(
                        NullPointerException.class,
                        () -> GenerationResponse.of(
                                null,
                                Language.EN,
                                false,
                                100L
                        )
                );
            }

            @Test
            @DisplayName("Should reject empty usernames list in constructor")
            void shouldRejectEmptyUsernamesListInConstructor() {
                // Act & Assert
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new GenerationResponse(
                                Collections.emptyList(),
                                Instant.now(),
                                Language.EN,
                                0,
                                false,
                                100L
                        )
                );
            }

            @Test
            @DisplayName("Should reject empty usernames list in factory method")
            void shouldRejectEmptyUsernamesListInFactoryMethod() {
                // Act & Assert
                assertThrows(
                        IllegalArgumentException.class,
                        () -> GenerationResponse.of(
                                Collections.emptyList(),
                                Language.EN,
                                false,
                                100L
                        )
                );
            }
        }
    }

    @Nested
    @DisplayName("getUsernameValues method")
    class GetUsernameValuesMethod {

        @Test
        @DisplayName("Should extract username values")
        void shouldExtractUsernameValues() {
            // Arrange
            GenerationResponse response = new GenerationResponse(
                    List.of(
                            createValidUsername("user123"),
                            createValidUsername("user456")
                    ),
                    Instant.now(),
                    Language.EN,
                    2,
                    false,
                    100L
            );

            // Act
            List<String> values = response.getUsernameValues();

            // Assert
            assertAll(
                    () -> assertEquals(2, values.size()),
                    () -> assertTrue(values.contains("user123")),
                    () -> assertTrue(values.contains("user456"))
            );
        }

        @Test
        @DisplayName("Should return list with correct size")
        void shouldReturnListWithCorrectSize() {
            // Arrange
            GenerationResponse response = new GenerationResponse(
                    List.of(
                            createValidUsername("user123"),
                            createValidUsername("user456"),
                            createValidUsername("user789")
                    ),
                    Instant.now(),
                    Language.EN,
                    3,
                    false,
                    100L
            );

            // Act
            List<String> values = response.getUsernameValues();

            // Assert
            assertEquals(3, values.size());
        }
    }
}
