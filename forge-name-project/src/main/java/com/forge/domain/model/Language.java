package com.forge.domain.model;

import lombok.Getter;

import java.util.Arrays;
import java.util.Locale;

@Getter
public enum Language {

    EN("English", "en", Locale.ENGLISH),
    ES("Spanish", "es", new Locale("es"));

    private final String displayName;
    private final String code;
    private final Locale locale;

    Language(String displayName, String code, Locale locale) {
        this.displayName = displayName;
        this.code = code;
        this.locale = locale;
    }

    public static Language fromCode(String code) {
        return Arrays.stream(values())
                .filter(lang -> lang.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow( () -> new IllegalArgumentException("Unknown language code: " + code));
    }

}
