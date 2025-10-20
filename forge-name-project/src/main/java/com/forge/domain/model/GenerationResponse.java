package com.forge.domain.model;

import java.time.Instant;
import java.util.List;

public record GenerationResponse(
        List<Username> usernames,
        Instant generatedAt,
        Language language,
        int totalGenerated,
        boolean cacheHit,
        long responseTimeMs
) {

    public GenerationResponse {
        validateNotNull(usernames, "Usernames");
        validateNotNull(language, "Language");
        if (usernames.isEmpty()) {
            throw new IllegalArgumentException("Usernames list cannot be empty");
        }
    }

    public static GenerationResponse of(
            List<Username> usernames,
            Language language,
            boolean cacheHit,
            long responseTimeMs
    ) {
        return new GenerationResponse(
                usernames,
                Instant.now(),
                language,
                usernames.size(),
                cacheHit,
                responseTimeMs
        );
    }

    public List<String> getUsernameValues() {
        return usernames.stream()
                .map(Username::value)
                .toList();
    }

    private static void validateNotNull(Object object, String fieldName) {
        if (object == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
    }
}
