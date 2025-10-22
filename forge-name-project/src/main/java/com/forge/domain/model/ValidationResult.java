package com.forge.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record ValidationResult (
        String username,
        boolean isValid,
        boolean isUnique,
        boolean isAppropriate,
        boolean isValidFormat,
        List<String> reasons,
        double confidenceScore,
        Instant validatedAt
) {
    public ValidationResult {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be null or blank");
        }

        if (reasons == null) {
            reasons = new ArrayList<>();
        }

        if (confidenceScore < 0.0 || confidenceScore > 1.0) {
            throw new IllegalArgumentException("Confidence score must be between 0.0 and 1.0");
        }

        if (validatedAt == null) {
            validatedAt = Instant.now();
        }

        reasons = List.copyOf(reasons);
    }

    public static ValidationResult valid(String username, Instant validatedAt) {
        return new ValidationResult(
                username,
                true,
                true,
                true,
                true,
                List.of(),
                0.98,
                validatedAt
        );
    }

    public static ValidationResult invalid(
            String username,
            boolean isUnique,
            boolean isAppropriate,
            boolean isValidFormat,
            List<String> reasons,
            Instant validatedAt
    ) {
        boolean isValid = isUnique && isAppropriate && isValidFormat;
        double confidenceScore = calculateConfidenceScore(isUnique, isAppropriate, isValidFormat);

        return new ValidationResult(
                username,
                isValid,
                isUnique,
                isAppropriate,
                isValidFormat,
                reasons,
                confidenceScore,
                validatedAt
        );
    }

    private static double calculateConfidenceScore(
            boolean isUnique,
            boolean isAppropriate,
            boolean isValidFormat
    ) {
        int passedChecks = (isUnique ? 1 : 0) + (isAppropriate ? 1 : 0) + (isValidFormat ? 1 : 0);

        return switch (passedChecks) {
            case 3 -> 0.98;
            case 2 -> 0.85;
            case 1 -> 0.50;
            default -> 0.10;
        };
    }
}
