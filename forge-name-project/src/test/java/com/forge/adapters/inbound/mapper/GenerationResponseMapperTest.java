package com.forge.adapters.inbound.mapper;

import com.forge.adapters.inbound.dto.UsernameResponseDto;
import com.forge.domain.model.GenerationResponse;
import com.forge.domain.model.Language;
import com.forge.domain.model.Username;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GenerationResponseMapper Tests")
class GenerationResponseMapperTest {

    private final GenerationResponseMapper mapper = new GenerationResponseMapper();

    @Test
    @DisplayName("Should Map Domain To Dto")
    void shouldMapDomainToDto() {
        // ARRANGE
        List<Username> usernames = List.of(
                Username.of("cleverpanda42", Language.EN),
                Username.of("swifteagle99", Language.EN)
        );
        GenerationResponse domain = GenerationResponse.of(
                usernames,
                Language.EN,
                true,
                45L
        );

        // ACT
        UsernameResponseDto dto = mapper.toDto(domain);

        // ASSERT
        assertThat(dto).isNotNull();
        assertThat(dto.usernames()).hasSize(2);
        assertThat(dto.usernames()).containsExactly("cleverpanda42", "swifteagle99");
        assertThat(dto.language()).isEqualTo("EN");
        assertThat(dto.totalGenerated()).isEqualTo(2);
        assertThat(dto.cacheHit()).isTrue();
        assertThat(dto.responseTimeMs()).isEqualTo(45L);
        assertThat(dto.generatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should Map Spanish Language To Dto")
    void shouldMapSpanishLanguageToDto() {
        // ARRANGE
        List<Username> usernames = List.of(
                Username.of("pandainteligente42", Language.ES)
        );
        GenerationResponse domain = GenerationResponse.of(
                usernames,
                Language.ES,
                false,
                120L
        );

        // ACT
        UsernameResponseDto dto = mapper.toDto(domain);

        // ASSERT
        assertThat(dto.language()).isEqualTo("ES");
        assertThat(dto.cacheHit()).isFalse();
    }
}