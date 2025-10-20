package com.forge.adapters.inbound.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GenerationRequestDto Tests")
class GenerationRequestDtoTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("Should Create Valid RequestDto")
    void shouldCreateValidRequestDto() {
        // ARRANGE & ACT
        GenerationRequestDto dto = new GenerationRequestDto("EN", 5);

        // ASSERT
        assertThat(dto).isNotNull();
        assertThat(dto.language()).isEqualTo("EN");
        assertThat(dto.count()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should Use Default Count When Count is Null")
    void shouldUseDefaultCountWhenNull() {
        // ARRANGE & ACT
        GenerationRequestDto dto = new GenerationRequestDto("EN", null);

        // ASSERT
        assertThat(dto.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should Fail Validation When Language Is Null")
    void shouldFailValidationWhenLanguageIsNull() {
        // ARRANGE
        GenerationRequestDto dto = new GenerationRequestDto(null, 5);

        // ACT
        var violations = validator.validate(dto);

        // ASSERT
        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .extracting("message")
                .contains("Language is required");
    }

    @Test
    @DisplayName("Should Fail Validation When Count Is Less Than One")
    void shouldFailValidationWhenCountIsLessThanOne() {
        // ARRANGE
        GenerationRequestDto dto = new GenerationRequestDto("EN", -1);

        // ACT
        var violations = validator.validate(dto);

        // ASSERT
        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .extracting("message")
                .anyMatch(msg -> msg.toString().contains("must be greater than or equal to 1"));
    }

    @Test
    @DisplayName("Should Fail Validation When Count Is Greater Than Ten")
    void shouldFailValidationWhenCountIsGreaterThanTen() {
        // ARRANGE
        GenerationRequestDto dto = new GenerationRequestDto("EN", 11);

        // ACT
        var violations = validator.validate(dto);

        // ASSERT
        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .extracting("message")
                .anyMatch(msg -> msg.toString().contains("must be less than or equal to 10"));
    }

    @Test
    @DisplayName("Should Fail Validation When Language Is Invalid")
    void shouldFailValidationWhenLanguageIsInvalid() {
        // ARRANGE
        GenerationRequestDto dto = new GenerationRequestDto("FR", 5);

        // ACT
        var violations = validator.validate(dto);

        // ASSERT
        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .extracting("message")
                .anyMatch(msg -> msg.toString().contains("must be one of: EN, ES"));
    }

}