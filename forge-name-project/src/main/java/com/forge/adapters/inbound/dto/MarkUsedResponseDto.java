package com.forge.adapters.inbound.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Response after marking username as used")
public record MarkUsedResponseDto(

        @Schema(
                description = "The username that was processed",
                example = "cleverpanda42"
        )
        @JsonProperty("username")
        String username,

        @Schema(
                description = "Whether the username was successfully marked as used",
                example = "true"
        )
        @JsonProperty("marked")
        boolean marked,

        @Schema(
                description = "Whether the username was already used before this request",
                example = "false"
        )
        @JsonProperty("wasAlreadyUsed")
        boolean wasAlreadyUsed,

        @Schema(
                description = "Timestamp when the username was marked as used",
                example = "2025-10-26T14:30:00Z"
        )
        @JsonProperty("markedAt")
        Instant markedAt,

        @Schema(
                description = "Human-readable message describing the result",
                example = "Username successfully marked as used"
        )
        @JsonProperty("message")
        String message
) {
}
