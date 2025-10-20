package com.forge.adapters.outbound.moderation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO for Perspective API request.
 * Represents the JSON payload sent to Google's Perspective Comment Analyzer API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerspectiveRequest {

    /**
     * The comment to analyze.
     */
    private Comment comment;

    /**
     * List of language codes (ISO 639-1) for the comment.
     * Example: ["es", "en"]
     */
    private List<String> languages;

    /**
     * Map of requested analysis attributes.
     * Keys: TOXICITY, SEVERE_TOXICITY, IDENTITY_ATTACK, INSULT, THREAT
     * Values: Empty object {} for default configuration
     */
    @JsonProperty("requestedAttributes")
    private Map<String, String> requestedAttributes;

    /**
     * If true, Google will not store the comment for future research.
     * Recommended: true for privacy compliance.
     */
    @JsonProperty("doNotStore")
    private Boolean doNotStore;

    /**
     * Nested class representing the comment text.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Comment {
        /**
         * The actual text content to analyze.
         */
        private String text;
    }

}
