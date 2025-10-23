package com.forge.adapters.inbound.graphql.type;

import java.time.Instant;
import java.util.List;

/**
 * GraphQL response type for username validation.
 * Matches the ValidationResponse type in schema.graphqls
 *
 * This is an inbound adapter type, converting domain ValidationResult
 * to GraphQL-compatible format
 */
public record ValidationResponse(
        String username,
        boolean isValid,
        boolean isUnique,
        boolean isAppropriate,
        boolean isValidFormat,
        List<String> reasons,
        double confidenceScore,
        String validatedAt
) {

    /**
     * Factory method to create GraphQL response from domain model.
     * Converts Instant to ISO 8601 string for GraphQL compatibility.
     *
     * @param username Username that was validated
     * @param isValid Overall validation result
     * @param isUnique Username uniqueness check result
     * @param isAppropriate Content moderation result
     * @param isValidFormat Format validation result
     * @param reasons List of validation failure reasons
     * @param confidenceScore Validation confidence (0.0-1.0)
     * @param validatedAt Timestamp of validation
     * @return ValidationResponse instance
     */
    public static ValidationResponse of(
            String username,
            boolean isValid,
            boolean isUnique,
            boolean isAppropriate,
            boolean isValidFormat,
            List<String> reasons,
            double confidenceScore,
            Instant validatedAt
    ) {
        return new ValidationResponse(
                username,
                isValid,
                isUnique,
                isAppropriate,
                isValidFormat,
                reasons,
                confidenceScore,
                validatedAt.toString()
        );
    }
}
