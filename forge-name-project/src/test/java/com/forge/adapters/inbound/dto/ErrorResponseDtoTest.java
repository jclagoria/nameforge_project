package com.forge.adapters.inbound.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ErrorResponseDto Tests")
class ErrorResponseDtoTest {

    @Test
    @DisplayName("Should Create Error ResponseDto")
    void shouldCreateErrorResponseDto() {
        // ARRANGE & ACT
        ErrorResponseDto dto = new ErrorResponseDto(
                "INVALID_REQUEST",
                "Count must be between 1 and 10",
                Instant.now(),
                "/api/v1/usernames/generate"
        );

        // ASSERT
        assertThat(dto).isNotNull();
        assertThat(dto.error()).isEqualTo("INVALID_REQUEST");
        assertThat(dto.message()).isEqualTo("Count must be between 1 and 10");
        assertThat(dto.timestamp()).isNotNull();
        assertThat(dto.path()).isEqualTo("/api/v1/usernames/generate");
    }
}