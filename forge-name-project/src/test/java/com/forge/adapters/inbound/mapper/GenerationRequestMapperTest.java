package com.forge.adapters.inbound.mapper;

import com.forge.adapters.inbound.dto.GenerationRequestDto;
import com.forge.domain.model.GenerationRequest;
import com.forge.domain.model.Language;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GenerationRequestMapper Tests")
class GenerationRequestMapperTest {

    private final GenerationRequestMapper mapper = new GenerationRequestMapper();

    @Test
    @DisplayName("Should Map Dto To Domain")
    void shouldMapDtoToDomain() {
        // ARRANGE
        GenerationRequestDto dto = new GenerationRequestDto("EN", 5);

        // ACT
        GenerationRequest domain = mapper.toDomain(dto);

        // ASSERT
        assertThat(domain).isNotNull();
        assertThat(domain.language()).isEqualTo(Language.EN);
        assertThat(domain.count()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should Map Spanish Language")
    void shouldMapSpanishLanguage() {
        // ARRANGE
        GenerationRequestDto dto = new GenerationRequestDto("ES", 3);

        // ACT
        GenerationRequest domain = mapper.toDomain(dto);

        // ASSERT
        assertThat(domain.language()).isEqualTo(Language.ES);
    }

    @Test
    @DisplayName("Should Throw Exception For Invalid Language")
    void shouldThrowExceptionForInvalidLanguage() {
        // ARRANGE
        GenerationRequestDto dto = new GenerationRequestDto("FR", 5);

        // ACT & ASSERT
        assertThatThrownBy(() -> mapper.toDomain(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid language");
    }

}