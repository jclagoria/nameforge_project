package com.forge.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@DisplayName("Language Tests")
class LanguageTest {

    @Test
    @DisplayName("should have English language")
    void shouldHaveEnglishLanguage() {

        Language language = Language.EN;

        assertThat(language).isNotNull();
        assertThat(language.name()).isEqualTo("EN");
    }

    @Test
    @DisplayName("should have Spanish language")
    void shouldHaveSpanishLanguage() {

        Language language = Language.ES;

        assertThat(language).isNotNull();
        assertThat(language.name()).isEqualTo("ES");
    }

    @Test
    @DisplayName("should parse language from string")
    void shouldParseLanguageFromString() {

        Language en = Language.valueOf("EN");
        Language es = Language.valueOf("ES");

        assertThat(en).isEqualTo(Language.EN);
        assertThat(es).isEqualTo(Language.ES);
    }

    @Test
    @DisplayName("should throw exception for invalid language")
    void shouldThrowExceptionForInvalidLanguage() {
        assertThatThrownBy(() -> Language.valueOf("FR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant com.forge.domain.model.Language.FR");
    }

    @Test
    @DisplayName("should get language from code")
    void shouldGetLanguageFromCode() {

        Language en = Language.fromCode("en");
        Language es = Language.fromCode("es");

        assertThat(en).isEqualTo(Language.EN);
        assertThat(es).isEqualTo(Language.ES);
    }

    @Test
    @DisplayName("should get language from code case insensitive")
    void shouldGetLanguageFromCodeCaseInsensitive() {

        Language enUpper = Language.fromCode("EN");
        Language enLower = Language.fromCode("en");
        Language enMixed = Language.fromCode("En");

        assertThat(enUpper).isEqualTo(Language.EN);
        assertThat(enLower).isEqualTo(Language.EN);
        assertThat(enMixed).isEqualTo(Language.EN);
    }

    @Test
    @DisplayName("should throw exception for invalid language code")
    void shouldThrowExceptionForInvalidLanguageCode() {
        assertThatThrownBy(() -> Language.fromCode("fr"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown language code: fr");
    }

    @Test
    @DisplayName("should throw exception for null language code")
    void shouldThrowExceptionForNullLanguageCode() {
        assertThatThrownBy(() -> Language.fromCode(null))
                 .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown language code: null");
    }

    @Test
    @DisplayName("should throw exception for empty language code")
    void shouldThrowExceptionForEmptyLanguageCode() {
        assertThatThrownBy(() -> Language.fromCode(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown language code: ");
    }

}