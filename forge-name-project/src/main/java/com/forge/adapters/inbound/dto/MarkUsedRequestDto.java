package com.forge.adapters.inbound.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Request to mark a username as used")
public record MarkUsedRequestDto(
        @Schema(
                description = "Username to mark as used",
                example = "cleverpanda42",
                pattern = "^[a-z0-9_-]{5,30}$"
        )
        @NotBlank(message = "Username is required")
        @Pattern(
                regexp = "^[a-z0-9_-]{5,30}$",
                message = "Username must be 5-30 characters (lowercase, numbers, underscore, hyphen)"
        )
        @JsonProperty("username")
        String username
) {
}
