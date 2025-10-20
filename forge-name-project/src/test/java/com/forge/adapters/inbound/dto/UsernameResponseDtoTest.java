package com.forge.adapters.inbound.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UsernameResponseDto Tests")
class UsernameResponseDtoTest {

    @Test
    @DisplayName("Should Create ResponseDto")
    void shouldCreateResponseDto() {
        // ARRANGE
        List<String> usernames = List.of("cleverpanda42", "swifteagle99");
        Instant generatedAt = Instant.now();
        String language = "EN";
        int totalGenerated = 2;
        boolean cacheHit = true;
        long responseTimeMs = 45L;

        // ACT
        UsernameResponseDto dto = new UsernameResponseDto(
                usernames,
                generatedAt,
                language,
                totalGenerated,
                cacheHit,
                responseTimeMs
        );

        // ASSERT
        assertThat(dto).isNotNull();
        assertThat(dto.usernames()).hasSize(2);
        assertThat(dto.usernames()).containsExactly("cleverpanda42", "swifteagle99");
        assertThat(dto.language()).isEqualTo("EN");
        assertThat(dto.totalGenerated()).isEqualTo(2);
        assertThat(dto.cacheHit()).isTrue();
        assertThat(dto.responseTimeMs()).isEqualTo(45L);
    }
}