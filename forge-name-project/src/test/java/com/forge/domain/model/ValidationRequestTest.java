package com.forge.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ValidationRequest Domain Model Tests")
class ValidationRequestTest {

    @Nested
    @DisplayName("Constructor Validation Tests")
    class ConstructorValidationTests {

        @Test
        @DisplayName("Should throw IllegalArgumentException when username is null")
        void shouldThrowExceptionWhenUsernameIsNull() {
            // Given
            String username = null;
            Language language = Language.EN;

            // When & Then
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ValidationRequest(username, language)
            );

            assertEquals("Username cannot be null or blank", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when username is blank")
        void shouldThrowExceptionWhenUsernameIsBlank() {
            // Given
            String username = "   ";
            Language language = Language.EN;

            // When & Then
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ValidationRequest(username, language)
            );

            assertEquals("Username cannot be null or blank", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when username is empty")
        void shouldThrowExceptionWhenUsernameIsEmpty() {
            // Given
            String username = "";
            Language language = Language.EN;

            // When & Then
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ValidationRequest(username, language)
            );

            assertEquals("Username cannot be null or blank", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when language is null")
        void shouldThrowExceptionWhenLanguageIsNull() {
            // Given
            String username = "testuser";
            Language language = null;

            // When & Then
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ValidationRequest(username, language)
            );

            assertEquals("Language cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("Should create valid ValidationRequest with valid username and language")
        void shouldCreateValidValidationRequestWithValidUsernameAndLanguage() {
            // Given
            String username = "testuser";
            Language language = Language.EN;

            // When
            ValidationRequest request = new ValidationRequest(username, language);

            // Then
            assertNotNull(request);
            assertEquals(username, request.username());
            assertEquals(language, request.language());
        }

        @Test
        @DisplayName("Should accept username with special characters")
        void shouldAcceptUsernameWithSpecialCharacters() {
            // Given
            String username = "user_123-test";
            Language language = Language.EN;

            // When
            ValidationRequest request = new ValidationRequest(username, language);

            // Then
            assertEquals(username, request.username());
        }

        @Test
        @DisplayName("Should accept very long username")
        void shouldAcceptVeryLongUsername() {
            // Given
            String username = "a".repeat(255);
            Language language = Language.EN;

            // When
            ValidationRequest request = new ValidationRequest(username, language);

            // Then
            assertEquals(username, request.username());
            assertEquals(255, request.username().length());
        }

        @Test
        @DisplayName("Should accept single character username")
        void shouldAcceptSingleCharacterUsername() {
            // Given
            String username = "a";
            Language language = Language.EN;

            // When
            ValidationRequest request = new ValidationRequest(username, language);

            // Then
            assertEquals(username, request.username());
        }
    }

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("Should create ValidationRequest using of factory method with valid inputs")
        void shouldCreateValidationRequestUsingOfFactoryMethodWithValidInputs() {
            // Given
            String username = "testuser";
            Language language = Language.EN;

            // When
            ValidationRequest request = ValidationRequest.of(username, language);

            // Then
            assertNotNull(request);
            assertEquals(username, request.username());
            assertEquals(language, request.language());
        }

        @Test
        @DisplayName("Should throw exception using of factory method when username is null")
        void shouldThrowExceptionUsingOfFactoryMethodWhenUsernameIsNull() {
            // Given
            String username = null;
            Language language = Language.EN;

            // When & Then
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ValidationRequest.of(username, language)
            );

            assertEquals("Username cannot be null or blank", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception using of factory method when language is null")
        void shouldThrowExceptionUsingOfFactoryMethodWhenLanguageIsNull() {
            // Given
            String username = "testuser";
            Language language = null;

            // When & Then
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ValidationRequest.of(username, language)
            );

            assertEquals("Language cannot be null", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Language Support Tests")
    class LanguageSupportTests {

        @Test
        @DisplayName("Should create ValidationRequest with English language")
        void shouldCreateValidationRequestWithEnglishLanguage() {
            // Given
            String username = "testuser";
            Language language = Language.EN;

            // When
            ValidationRequest request = ValidationRequest.of(username, language);

            // Then
            assertEquals(Language.EN, request.language());
            assertEquals("English", request.language().getDisplayName());
            assertEquals("en", request.language().getCode());
        }

        @Test
        @DisplayName("Should create ValidationRequest with Spanish language")
        void shouldCreateValidationRequestWithSpanishLanguage() {
            // Given
            String username = "usuario";
            Language language = Language.ES;

            // When
            ValidationRequest request = ValidationRequest.of(username, language);

            // Then
            assertEquals(Language.ES, request.language());
            assertEquals("Spanish", request.language().getDisplayName());
            assertEquals("es", request.language().getCode());
        }

        @Test
        @DisplayName("Should verify all supported languages can be used")
        void shouldVerifyAllSupportedLanguagesCanBeUsed() {
            // Given & When & Then
            for (Language language : Language.values()) {
                ValidationRequest request = ValidationRequest.of("testuser", language);
                assertNotNull(request);
                assertEquals(language, request.language());
            }
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
            Language language = Language.EN;

            // When
            ValidationRequest request1 = new ValidationRequest(username, language);
            ValidationRequest request2 = new ValidationRequest(username, language);

            // Then
            assertEquals(request1, request2);
            assertEquals(request1.hashCode(), request2.hashCode());
        }

        @Test
        @DisplayName("Should maintain inequality for records with different usernames")
        void shouldMaintainInequalityForRecordsWithDifferentUsernames() {
            // Given
            Language language = Language.EN;

            // When
            ValidationRequest request1 = ValidationRequest.of("user1", language);
            ValidationRequest request2 = ValidationRequest.of("user2", language);

            // Then
            assertNotEquals(request1, request2);
        }

        @Test
        @DisplayName("Should maintain inequality for records with different languages")
        void shouldMaintainInequalityForRecordsWithDifferentLanguages() {
            // Given
            String username = "testuser";

            // When
            ValidationRequest request1 = ValidationRequest.of(username, Language.EN);
            ValidationRequest request2 = ValidationRequest.of(username, Language.ES);

            // Then
            assertNotEquals(request1, request2);
        }

        @Test
        @DisplayName("Should generate meaningful toString representation")
        void shouldGenerateMeaningfulToStringRepresentation() {
            // Given
            String username = "testuser";
            Language language = Language.EN;

            // When
            ValidationRequest request = ValidationRequest.of(username, language);
            String toString = request.toString();

            // Then
            assertNotNull(toString);
            assertTrue(toString.contains("testuser"));
            assertTrue(toString.contains("EN"));
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Tests")
    class EdgeCasesAndBoundaryTests {

        @Test
        @DisplayName("Should handle username with numbers only")
        void shouldHandleUsernameWithNumbersOnly() {
            // Given
            String username = "123456";
            Language language = Language.EN;

            // When
            ValidationRequest request = ValidationRequest.of(username, language);

            // Then
            assertEquals(username, request.username());
        }

        @Test
        @DisplayName("Should handle username with mixed alphanumeric characters")
        void shouldHandleUsernameWithMixedAlphanumericCharacters() {
            // Given
            String username = "User123Test456";
            Language language = Language.EN;

            // When
            ValidationRequest request = ValidationRequest.of(username, language);

            // Then
            assertEquals(username, request.username());
        }

        @Test
        @DisplayName("Should handle username with underscores")
        void shouldHandleUsernameWithUnderscores() {
            // Given
            String username = "user_name_test";
            Language language = Language.EN;

            // When
            ValidationRequest request = ValidationRequest.of(username, language);

            // Then
            assertEquals(username, request.username());
        }

        @Test
        @DisplayName("Should handle username with hyphens")
        void shouldHandleUsernameWithHyphens() {
            // Given
            String username = "user-name-test";
            Language language = Language.EN;

            // When
            ValidationRequest request = ValidationRequest.of(username, language);

            // Then
            assertEquals(username, request.username());
        }

        @Test
        @DisplayName("Should handle username with dots")
        void shouldHandleUsernameWithDots() {
            // Given
            String username = "user.name.test";
            Language language = Language.EN;

            // When
            ValidationRequest request = ValidationRequest.of(username, language);

            // Then
            assertEquals(username, request.username());
        }

        @Test
        @DisplayName("Should handle username with uppercase letters")
        void shouldHandleUsernameWithUppercaseLetters() {
            // Given
            String username = "TESTUSER";
            Language language = Language.EN;

            // When
            ValidationRequest request = ValidationRequest.of(username, language);

            // Then
            assertEquals(username, request.username());
        }

        @Test
        @DisplayName("Should handle username with lowercase letters")
        void shouldHandleUsernameWithLowercaseLetters() {
            // Given
            String username = "testuser";
            Language language = Language.EN;

            // When
            ValidationRequest request = ValidationRequest.of(username, language);

            // Then
            assertEquals(username, request.username());
        }

        @Test
        @DisplayName("Should handle username with mixed case letters")
        void shouldHandleUsernameWithMixedCaseLetters() {
            // Given
            String username = "TestUser";
            Language language = Language.EN;

            // When
            ValidationRequest request = ValidationRequest.of(username, language);

            // Then
            assertEquals(username, request.username());
        }

        @Test
        @DisplayName("Should accept username with leading and trailing spaces")
        void shouldAcceptUsernameWithLeadingAndTrailingSpaces() {
            // Given
            String username = " test "; // has content, so not blank

            // When
            ValidationRequest request = ValidationRequest.of(username, Language.EN);

            // Then
            assertEquals(username, request.username());
            assertEquals(" test ", request.username()); // preserves spaces
        }

        @Test
        @DisplayName("Should reject username with only whitespace")
        void shouldRejectUsernameWithOnlyWhitespace() {
            // Given
            String username = "    "; // only spaces, considered blank

            // When & Then
            assertThrows(
                IllegalArgumentException.class,
                () -> ValidationRequest.of(username, Language.EN)
            );
        }

        @Test
        @DisplayName("Should handle username starting with number")
        void shouldHandleUsernameStartingWithNumber() {
            // Given
            String username = "123user";
            Language language = Language.EN;

            // When
            ValidationRequest request = ValidationRequest.of(username, language);

            // Then
            assertEquals(username, request.username());
        }
    }

    @Nested
    @DisplayName("Immutability Tests")
    class ImmutabilityTests {

        @Test
        @DisplayName("Should maintain username immutability")
        void shouldMaintainUsernameImmutability() {
            // Given
            String originalUsername = "testuser";
            ValidationRequest request = ValidationRequest.of(originalUsername, Language.EN);

            // When
            String retrievedUsername = request.username();

            // Then
            assertEquals(originalUsername, retrievedUsername);
            assertSame(originalUsername, retrievedUsername);
        }

        @Test
        @DisplayName("Should maintain language immutability")
        void shouldMaintainLanguageImmutability() {
            // Given
            Language originalLanguage = Language.EN;
            ValidationRequest request = ValidationRequest.of("testuser", originalLanguage);

            // When
            Language retrievedLanguage = request.language();

            // Then
            assertEquals(originalLanguage, retrievedLanguage);
            assertSame(originalLanguage, retrievedLanguage);
        }
    }
}