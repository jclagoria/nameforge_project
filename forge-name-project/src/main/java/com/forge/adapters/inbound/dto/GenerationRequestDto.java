package com.forge.adapters.inbound.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Request to generate usernames")
public record GenerationRequestDto(

        @Schema(
                description = "Language code for username generation",
                example = "EN",
                allowableValues = {"EN", "ES"},
                required = true
        )
        @NotNull(message = "Language is required")
        @Pattern(regexp = "^(EN|ES)$", message = "Language must be one of: EN, ES")
        String language,

        @Schema(
                description = "Number of usernames to generate",
                example = "5",
                minimum = "1",
                maximum = "10",
                defaultValue = "1"
        )
        @Min(value = 1, message = "Count must be greater than or equal to 1")
        @Max(value = 10, message = "Count must be less than or equal to 10")
        Integer count
) {
    public GenerationRequestDto {
        if (count == null) {
            count = 1;
        }
    }
}
