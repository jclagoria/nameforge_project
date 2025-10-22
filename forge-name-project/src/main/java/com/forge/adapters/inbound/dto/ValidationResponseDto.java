package com.forge.adapters.inbound.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Username validation response")
public record ValidationResponseDto(

        @Schema(description = "The username that was validated", example = "cleverpanda42")
        @JsonProperty("username")
        String username,

        @Schema(description = "Overall validation result", example = "true")
        @JsonProperty("isValid")
        boolean isValid,

        @Schema(description = "Username is unique (not already taken)", example = "true")
        @JsonProperty("isUnique")
        boolean isUnique,

        @Schema(description = "Username passes content moderation", example = "true")
        @JsonProperty("isAppropriate")
        boolean isAppropriate,

        @Schema(description = "Username matches format requirements", example = "true")
        @JsonProperty("isValidFormat")
        boolean isValidFormat,

        @Schema(description = "List of validation failure reasons", example = "[]")
        @JsonProperty("reasons")
        List<String> reasons,

        @Schema(description = "Confidence score (0.0-1.0)", example = "0.98")
        @JsonProperty("confidenceScore")
        double confidenceScore,

        @Schema(description = "Timestamp of validation", example = "2024-09-24T10:30:00Z")
        @JsonProperty("validatedAt")
        Instant validatedAt
) {

}