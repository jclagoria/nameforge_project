package com.forge.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@DisplayName("Username Tests")
class UsernameTest {

    @Test
    @DisplayName("Should create a valid Username")
    void shouldCreateValidUsername() {
        String value = "cleverpanda42";
        Language language = Language.EN;
        PatternType patternType = PatternType.CLASSIC;

        Username username = Username.of(value, language, patternType);

        assertThat(username).isNotNull();
        assertThat(username.value()).isEqualTo(value);
        assertThat(username.language()).isEqualTo(language);
        assertThat(username.patternType()).isEqualTo(patternType);
    }

    @Test
    @DisplayName("Should create a Username with Default Pattern")
    void shouldCreateUsernameWithDefaultPattern() {
        // ARRANGE
        String value = "swifteagle99";
        Language language = Language.EN;

        // ACT
        Username username = Username.of(value, language);

        // ASSERT
        assertThat(username).isNotNull();
        assertThat(username.value()).isEqualTo(value);
        assertThat(username.patternType()).isEqualTo(PatternType.CLASSIC);
    }

    @Test
    @DisplayName("Should create Username with all PatternTypes")
    void shouldCreateUsernameWithAllPatternTypes() {
        String value = "test_user";
        Language language = Language.EN;

        Username classic = Username.of(value, language, PatternType.CLASSIC);
        Username separator = Username.of(value, language, PatternType.SEPARATOR);
        Username wordplay = Username.of(value, language, PatternType.WORDPLAY);

        assertThat(classic.patternType()).isEqualTo(PatternType.CLASSIC);
        assertThat(separator.patternType()).isEqualTo(PatternType.SEPARATOR);
        assertThat(wordplay.patternType()).isEqualTo(PatternType.WORDPLAY);
    }

    @Test
    @DisplayName("Should create Username with all Languages")
    void shouldCreateUsernameWithAllLanguages() {
        String value = "test_user";

        Username english = Username.of(value, Language.EN);
        Username spanish = Username.of(value, Language.ES);

        assertThat(english.language()).isEqualTo(Language.EN);
        assertThat(spanish.language()).isEqualTo(Language.ES);
    }

    @Test
    @DisplayName("Should throw exception for null value")
    void shouldThrowExceptionForNullValue() {
        assertThatThrownBy(() -> Username.of(null, Language.EN, PatternType.CLASSIC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username value cannot be null");
    }

    @Test
    @DisplayName("Should throw exception for null language")
    void shouldThrowExceptionForNullLanguage() {
        assertThatThrownBy(() -> Username.of("testuser", null, PatternType.CLASSIC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Language cannot be null");
    }

    @Test
    @DisplayName("Should throw exception for null patternType")
    void shouldThrowExceptionForNullPatternType() {
        assertThatThrownBy(() -> Username.of("testuser", Language.EN, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PatternType cannot be null");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "ab", "a"})
    @DisplayName("Should throw exception for usernames shorter than 5 characters")
    void shouldThrowExceptionForShortUsernames(String value) {
        assertThatThrownBy(() -> Username.of(value, Language.EN, PatternType.CLASSIC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be between 5 and 30");
    }

    @Test
    @DisplayName("Should throw exception for usernames longer than 30 characters")
    void shouldThrowExceptionForLongUsernames() {
        String longValue = "a".repeat(31);
        assertThatThrownBy(() -> Username.of(longValue, Language.EN, PatternType.CLASSIC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be between 5 and 30");
    }

    @ParameterizedTest
    @ValueSource(strings = {"User123", "TEST_USER", "user@123", "user.name", "user$special", "user#tag", "emoji🎮user", "user-CAPS"})
    @DisplayName("Should throw exception for invalid characters")
    void shouldThrowExceptionForInvalidCharacters(String value) {
        assertThatThrownBy(() -> Username.of(value, Language.EN, PatternType.CLASSIC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must contain only lowercase");
    }

    @Test
    @DisplayName("Should throw exception for Unicode characters (Chinese)")
    void shouldThrowExceptionForUnicodeCharacters() {
        assertThatThrownBy(() -> Username.of("用户名用户", Language.EN, PatternType.CLASSIC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must contain only lowercase");
    }

    @ParameterizedTest
    @ValueSource(strings = {"user-name_99", "test_user", "user-123", "user_name", "lowercase123"})
    @DisplayName("Should accept valid usernames with allowed characters")
    void shouldAcceptValidUsernames(String value) {
        Username username = Username.of(value, Language.EN, PatternType.CLASSIC);
        assertThat(username.value()).isEqualTo(value);
    }

    @Test
    @DisplayName("Should accept username with exactly 5 characters")
    void shouldAcceptMinimumLengthUsername() {
        Username username = Username.of("test1", Language.EN, PatternType.CLASSIC);
        assertThat(username.value()).hasSize(5);
    }

    @Test
    @DisplayName("Should accept username with exactly 30 characters")
    void shouldAcceptMaximumLengthUsername() {
        String maxLength = "a".repeat(30);
        Username username = Username.of(maxLength, Language.EN, PatternType.CLASSIC);
        assertThat(username.value()).hasSize(30);
    }

    @Test
    @DisplayName("Should implement equals correctly for same values")
    void shouldImplementEqualsCorrectlyForSameValues() {
        Username username1 = Username.of("test_user", Language.EN, PatternType.CLASSIC);
        Username username2 = Username.of("test_user", Language.EN, PatternType.CLASSIC);

        assertThat(username1).isEqualTo(username2);
    }

    @Test
    @DisplayName("Should not be equal for different values")
    void shouldNotBeEqualForDifferentValues() {
        Username username1 = Username.of("test_user1", Language.EN, PatternType.CLASSIC);
        Username username2 = Username.of("test_user2", Language.EN, PatternType.CLASSIC);

        assertThat(username1).isNotEqualTo(username2);
    }

    @Test
    @DisplayName("Should not be equal for different languages")
    void shouldNotBeEqualForDifferentLanguages() {
        Username username1 = Username.of("test_user", Language.EN, PatternType.CLASSIC);
        Username username2 = Username.of("test_user", Language.ES, PatternType.CLASSIC);

        assertThat(username1).isNotEqualTo(username2);
    }

    @Test
    @DisplayName("Should not be equal for different pattern types")
    void shouldNotBeEqualForDifferentPatternTypes() {
        Username username1 = Username.of("test_user", Language.EN, PatternType.CLASSIC);
        Username username2 = Username.of("test_user", Language.EN, PatternType.SEPARATOR);

        assertThat(username1).isNotEqualTo(username2);
    }

    @Test
    @DisplayName("Should have same hashCode for equal objects")
    void shouldHaveSameHashCodeForEqualObjects() {
        Username username1 = Username.of("test_user", Language.EN, PatternType.CLASSIC);
        Username username2 = Username.of("test_user", Language.EN, PatternType.CLASSIC);

        assertThat(username1.hashCode()).isEqualTo(username2.hashCode());
    }

    @Test
    @DisplayName("Should have different hashCode for different objects")
    void shouldHaveDifferentHashCodeForDifferentObjects() {
        Username username1 = Username.of("test_user1", Language.EN, PatternType.CLASSIC);
        Username username2 = Username.of("test_user2", Language.EN, PatternType.CLASSIC);

        assertThat(username1.hashCode()).isNotEqualTo(username2.hashCode());
    }

    @Test
    @DisplayName("Should implement toString correctly")
    void shouldImplementToStringCorrectly() {
        Username username = Username.of("test_user", Language.EN, PatternType.CLASSIC);
        String toString = username.toString();

        assertThat(toString).contains("test_user");
        assertThat(toString).contains("EN");
        assertThat(toString).contains("CLASSIC");
    }

    @Test
    @DisplayName("Should be reflexive for equals")
    void shouldBeReflexiveForEquals() {
        Username username = Username.of("test_user", Language.EN, PatternType.CLASSIC);

        assertThat(username).isEqualTo(username);
    }

    @Test
    @DisplayName("Should be symmetric for equals")
    void shouldBeSymmetricForEquals() {
        Username username1 = Username.of("test_user", Language.EN, PatternType.CLASSIC);
        Username username2 = Username.of("test_user", Language.EN, PatternType.CLASSIC);

        assertThat(username1.equals(username2)).isTrue();
        assertThat(username2.equals(username1)).isTrue();
    }

    @Test
    @DisplayName("Should be transitive for equals")
    void shouldBeTransitiveForEquals() {
        Username username1 = Username.of("test_user", Language.EN, PatternType.CLASSIC);
        Username username2 = Username.of("test_user", Language.EN, PatternType.CLASSIC);
        Username username3 = Username.of("test_user", Language.EN, PatternType.CLASSIC);

        assertThat(username1).isEqualTo(username2);
        assertThat(username2).isEqualTo(username3);
        assertThat(username1).isEqualTo(username3);
    }

    @Test
    @DisplayName("Should not be equal to null")
    void shouldNotBeEqualToNull() {
        Username username = Username.of("test_user", Language.EN, PatternType.CLASSIC);

        assertThat(username).isNotEqualTo(null);
    }

    @Test
    @DisplayName("Should not be equal to object of different type")
    void shouldNotBeEqualToObjectOfDifferentType() {
        Username username = Username.of("test_user", Language.EN, PatternType.CLASSIC);
        String notAUsername = "test_user";

        assertThat(username).isNotEqualTo(notAUsername);
    }

    @Test
    @DisplayName("Should throw exception when all fields are null")
    void shouldThrowExceptionWhenAllFieldsAreNull() {
        assertThatThrownBy(() -> Username.of(null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }

    @Test
    @DisplayName("Should throw exception for null value in two-parameter factory method")
    void shouldThrowExceptionForNullValueInTwoParameterFactory() {
        assertThatThrownBy(() -> Username.of(null, Language.EN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username value cannot be null");
    }

    @Test
    @DisplayName("Should throw exception for null language in two-parameter factory method")
    void shouldThrowExceptionForNullLanguageInTwoParameterFactory() {
        assertThatThrownBy(() -> Username.of("test_user", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Language cannot be null");
    }

}