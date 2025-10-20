package com.forge.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GenerationRequest Tests")
class GenerationRequestTest {

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("Should create with valid language and count")
        void shouldCreateWithValidLanguageAndCount() {
            // Arrange & Act
            GenerationRequest request = new GenerationRequest(Language.EN, 5);

            // Assert
            assertNotNull(request);
        }

        @Test
        @DisplayName("Should store language")
        void shouldStoreLanguage() {
            // Arrange
            Language expectedLanguage = Language.ES;

            // Act
            GenerationRequest request = new GenerationRequest(expectedLanguage, 5);

            // Assert
            assertEquals(expectedLanguage, request.language());
        }

        @Test
        @DisplayName("Should store count")
        void shouldStoreCount() {
            // Arrange
            int expectedCount = 5;

            // Act
            GenerationRequest request = new GenerationRequest(Language.EN, expectedCount);

            // Assert
            assertEquals(expectedCount, request.count());
        }
    }

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("Should create with language and count")
        void shouldCreateWithLanguageAndCount() {
            // Arrange
            Language language = Language.EN;
            int count = 3;

            // Act
            GenerationRequest request = GenerationRequest.of(language, count);

            // Assert
            assertAll(
                    () -> assertEquals(language, request.language()),
                    () -> assertEquals(count, request.count())
            );
        }

        @Test
        @DisplayName("Should create with language using default count")
        void shouldCreateWithLanguageUsingDefaultCount() {
            // Arrange
            Language language = Language.EN;

            // Act
            GenerationRequest request = GenerationRequest.of(language);

            // Assert
            assertAll(
                    () -> assertEquals(language, request.language()),
                    () -> assertEquals(1, request.count())
            );
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Nested
        @DisplayName("Language validation")
        class LanguageValidation {

            @Test
            @DisplayName("Should reject null language")
            void shouldRejectNullLanguage() {
                // Act & Assert
                assertThrows(
                        NullPointerException.class,
                        () -> new GenerationRequest(null, 5)
                );
            }

            @Test
            @DisplayName("Should reject null language in factory method with count")
            void shouldRejectNullLanguageInFactoryMethodWithCount() {
                // Act & Assert
                assertThrows(
                        NullPointerException.class,
                        () -> GenerationRequest.of(null, 5)
                );
            }

            @Test
            @DisplayName("Should reject null language in factory method")
            void shouldRejectNullLanguageInFactoryMethod() {
                // Act & Assert
                assertThrows(
                        NullPointerException.class,
                        () -> GenerationRequest.of(null)
                );
            }
        }

        @Nested
        @DisplayName("Count validation")
        class CountValidation {

            @Test
            @DisplayName("Should accept minimum count")
            void shouldAcceptMinimumCount() {
                // Act & Assert
                assertDoesNotThrow(() -> new GenerationRequest(Language.EN, 1));
            }

            @Test
            @DisplayName("Should accept maximum count")
            void shouldAcceptMaximumCount() {
                // Act & Assert
                assertDoesNotThrow(() -> new GenerationRequest(Language.EN, 10));
            }

            @Test
            @DisplayName("Should reject count below minimum")
            void shouldRejectCountBelowMinimum() {
                // Act & Assert
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new GenerationRequest(Language.EN, 0)
                );
            }

            @Test
            @DisplayName("Should reject negative count")
            void shouldRejectNegativeCount() {
                // Act & Assert
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new GenerationRequest(Language.EN, -1)
                );
            }

            @Test
            @DisplayName("Should reject count above maximum")
            void shouldRejectCountAboveMaximum() {
                // Act & Assert
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new GenerationRequest(Language.EN, 11)
                );
            }
        }
    }
}
