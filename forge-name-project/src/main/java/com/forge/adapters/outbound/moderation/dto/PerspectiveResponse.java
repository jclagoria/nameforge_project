package com.forge.adapters.outbound.moderation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO for Perspective API response.
 * Represents the JSON payload received from Google's Perspective Comment Analyzer API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PerspectiveResponse {

    /**
     * Map of attribute scores (TOXICITY, SEVERE_TOXICITY, etc.).
     * Key: Attribute name (e.g., "TOXICITY")
     * Value: AttributeScore object with span and summary scores
     */
    @JsonProperty("attributeScores")
    private Map<String, AttributeScore> attributeScores;

    /**
     * Languages specified in the request.
     */
    private List<String> languages;

    /**
     * Languages detected by the API.
     */
    @JsonProperty("detectedLanguages")
    private List<String> detectedLanguages;

    /**
     * Represents the score for a single attribute (e.g., TOXICITY).
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttributeScore {

        /**
         * Scores for each text span (for partial text analysis).
         */
        @JsonProperty("spanScores")
        private List<SpanScore> spanScores;

        /**
         * Overall summary score for the entire text.
         */
        @JsonProperty("summaryScore")
        private Score summaryScore;
    }

    /**
     * Represents a score for a specific text span.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpanScore {

        /**
         * Start position of the span (character index).
         */
        private Integer begin;

        /**
         * End position of the span (character index).
         */
        private Integer end;

        /**
         * The toxicity score for this span.
         */
        private Score score;
    }

    /**
     * Represents a probability score.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Score {

        /**
         * Probability value (0.0 to 1.0).
         * Higher values indicate higher likelihood of toxicity.
         */
        private Double value;

        /**
         * Type of score (typically "PROBABILITY").
         */
        private String type;
    }

}
