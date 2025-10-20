package com.forge.adapters.inbound.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Response containing generated usernames and metadata")
public record UsernameResponseDto(
        @Schema(
                description = "List of generated usernames",
                example = "[\"CoolGamer123\", \"EpicNinja456\", \"FastRunner789\"]"
        )
        @JsonProperty("usernames")
        List<String> usernames,

        @Schema(
                description = "Timestamp when usernames were generated",
                example = "2025-10-16T16:47:03.123Z"
        )
        @JsonProperty("generatedAt")
        Instant generatedAt,

        @Schema(
                description = "Language code used for generation",
                example = "EN"
        )
        @JsonProperty("language")
        String language,

        @Schema(
                description = "Total number of usernames generated",
                example = "5"
        )
        @JsonProperty("totalGenerated")
        int totalGenerated,

        @Schema(
                description = "Indicates if usernames were retrieved from cache",
                example = "false"
        )
        @JsonProperty("cacheHit")
        boolean cacheHit,

        @Schema(
                description = "Time taken to generate usernames in milliseconds",
                example = "245"
        )
        @JsonProperty("responseTimeMs")
        long responseTimeMs
) {
}
