package com.forge.adapters.outbound.moderation.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModerationResponse {

    private String id;
    private String model;
    private List<ModerationResult> results;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ModerationResult {
        private boolean flagged;
        private Categories categories;

        @JsonProperty("category_scores")
        private CategoryScores categoryScores;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Categories {
        private boolean hate;

        @JsonProperty("hate/threatening")
        private boolean hateThreatening;

        private boolean harassment;

        @JsonProperty("harassment/threatening")
        private boolean harassmentThreatening;

        @JsonProperty("self-harm")
        private boolean selfHarm;

        @JsonProperty("self-harm/intent")
        private boolean selfHarmIntent;

        @JsonProperty("self-harm/instructions")
        private boolean selfHarmInstructions;

        private boolean sexual;

        @JsonProperty("sexual/minors")
        private boolean sexualMinors;

        private boolean violence;

        @JsonProperty("violence/graphic")
        private boolean violenceGraphic;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryScores {

        private double hate;
        private double sexual;
        private double violence;
        private double harassment;

        @JsonProperty("self-harm")
        private double selfHarm;
    }

}
