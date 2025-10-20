package com.forge.domain.model;

import java.util.regex.Pattern;

public record Username(
        String value,
        Language language,
        PatternType patternType
) {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9_-]{5,30}$");
    private static final int MIN_LENGTH = 5;
    private static final int MAX_LENGTH = 30;

    public Username {
        validateNotNull(value, "Username value");
        validateNotNull(language, "Language");
        validateNotNull(patternType, "PatternType");
        validateFormat(value);
    }

    public static Username of(String value, Language language, PatternType patternType) {
        return new Username(value, language, patternType);
    }

    public static Username of(String value, Language language) {
        return new Username(value, language, PatternType.CLASSIC);
    }

    private static void validateNotNull(Object obj, String fieldName) {
        if (obj == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
    }

    private static void validateFormat(String value) {
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(String
                    .format("Username (%s) must be between %s and %s",
                            value,
                            MIN_LENGTH,
                            MAX_LENGTH));
        }

        if (!USERNAME_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Username must contain only lowercase " +
                    "alphanumeric characters, underscores, or hyphens");
        }
    }

}
