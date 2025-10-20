package com.forge.domain.model;

public record GenerationRequest(
        Language language,
        int count
) {
    private static final int DEFAULT_COUNT = 1;
    private static final int MIN_COUNT = 1;
    private static final int MAX_COUNT = 10;

    public GenerationRequest {
        validateNotNull(language, "Language");
        validateCount(count);
    }

    public static GenerationRequest of(Language language, int count) {
        return new GenerationRequest(language, count);
    }

    public static GenerationRequest of(Language language) {
        return new GenerationRequest(language, DEFAULT_COUNT);
    }

    private static void validateNotNull(Object object, String fieldName) {
        if (object == null) {
            throw new NullPointerException(fieldName + " cannot be null");
        }
    }

    private static void validateCount(int count) {
        if (count < MIN_COUNT || count > MAX_COUNT) {
            throw new IllegalArgumentException(String
                    .format("Count must be between %d and %d", MIN_COUNT, MAX_COUNT));
        }
    }

}
