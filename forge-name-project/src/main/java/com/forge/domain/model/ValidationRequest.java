package com.forge.domain.model;

public record ValidationRequest(
        String username,
        Language language
) {
    public ValidationRequest {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be null or blank");
        }
        if (language == null) {
            throw new IllegalArgumentException("Language cannot be null");
        }
    }

    public static ValidationRequest of(String username, Language language) {
        return new ValidationRequest(username, language);
    }
}
