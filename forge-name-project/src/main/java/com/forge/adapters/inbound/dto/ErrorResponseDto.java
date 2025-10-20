package com.forge.adapters.inbound.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Error response with details")
public record ErrorResponseDto(
        @Schema(
                description = "Error type or category",
                example = "ValidationError"
        )
        @JsonProperty("error")
        String error,

        @Schema(
                description = "Detailed error message describing what went wrong",
                example = "Validation failed: Language is required"
        )
        @JsonProperty("message")
        String message,

        @Schema(
                description = "Timestamp when the error occurred",
                example = "2025-10-16T16:47:03.123Z"
        )
        @JsonProperty("timestamp")
        Instant timestamp,

        @Schema(
                description = "Request path that caused the error",
                example = "/api/v1/usernames/generate"
        )
        @JsonProperty("path")
        String path
) {
}
