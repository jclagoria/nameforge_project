package com.forge.domain.fixtures;

import com.forge.domain.model.GenerationRequest;
import com.forge.domain.model.GenerationResponse;
import com.forge.domain.model.Language;
import com.forge.domain.model.PatternType;
import com.forge.domain.model.Username;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Test data fixtures for creating test objects across test suites.
 * Reduces duplication and provides consistent test data creation.
 */
public class TestDataFixtures {

    // ==================== Username Builders ====================

    /**
     * Creates a username with default language (EN) and pattern (CLASSIC).
     */
    public static Username username(String value) {
        return Username.of(value, Language.EN, PatternType.CLASSIC);
    }

    /**
     * Creates a username with specified language and default pattern (CLASSIC).
     */
    public static Username username(String value, Language language) {
        return Username.of(value, language, PatternType.CLASSIC);
    }

    /**
     * Creates a username with all parameters specified.
     */
    public static Username username(String value, Language language, PatternType pattern) {
        return Username.of(value, language, pattern);
    }

    // ==================== Bulk Username Creation ====================

    /**
     * Creates a list of usernames with default language (EN) and pattern (CLASSIC).
     * Usernames are numbered: prefix_01, prefix_02, etc.
     */
    public static List<Username> usernames(String prefix, int count) {
        return IntStream.rangeClosed(1, count)
            .mapToObj(i -> username(String.format("%s_%02d", prefix, i)))
            .toList();
    }

    /**
     * Creates a list of usernames with specified language and default pattern (CLASSIC).
     */
    public static List<Username> usernamesWithLanguage(String prefix, int count, Language language) {
        return IntStream.rangeClosed(1, count)
            .mapToObj(i -> username(String.format("%s_%02d", prefix, i), language))
            .toList();
    }

    /**
     * Creates a list of usernames with specified pattern and default language (EN).
     */
    public static List<Username> usernamesWithPattern(String prefix, int count, PatternType pattern) {
        return IntStream.rangeClosed(1, count)
            .mapToObj(i -> username(String.format("%s_%02d", prefix, i), Language.EN, pattern))
            .toList();
    }

    // ==================== Specialized Username Sets ====================

    /**
     * Creates usernames with all three pattern types (CLASSIC, SEPARATOR, WORDPLAY).
     */
    public static List<Username> mixedPatternUsernames(String prefix) {
        return List.of(
            username(prefix + "_classic", Language.EN, PatternType.CLASSIC),
            username(prefix + "_separator", Language.EN, PatternType.SEPARATOR),
            username(prefix + "_wordplay", Language.EN, PatternType.WORDPLAY)
        );
    }

    /**
     * Creates usernames with both languages (EN, ES).
     */
    public static List<Username> mixedLanguageUsernames(String prefix) {
        return List.of(
            username(prefix + "_en", Language.EN, PatternType.CLASSIC),
            username(prefix + "_es", Language.ES, PatternType.CLASSIC)
        );
    }

    /**
     * Creates usernames with all combinations of languages and patterns (6 total).
     */
    public static List<Username> allCombinationsUsernames(String prefix) {
        return List.of(
            username(prefix + "_en_classic", Language.EN, PatternType.CLASSIC),
            username(prefix + "_en_separator", Language.EN, PatternType.SEPARATOR),
            username(prefix + "_en_wordplay", Language.EN, PatternType.WORDPLAY),
            username(prefix + "_es_classic", Language.ES, PatternType.CLASSIC),
            username(prefix + "_es_separator", Language.ES, PatternType.SEPARATOR),
            username(prefix + "_es_wordplay", Language.ES, PatternType.WORDPLAY)
        );
    }

    // ==================== Constants ====================

    public static final String DEFAULT_USERNAME = "testuser123";
    public static final String ENGLISH_USERNAME = "english_user";
    public static final String SPANISH_USERNAME = "usuario_espanol";

    // Common test usernames
    public static Username defaultUsername() {
        return username(DEFAULT_USERNAME);
    }

    public static Username englishUsername() {
        return username(ENGLISH_USERNAME, Language.EN);
    }

    public static Username spanishUsername() {
        return username(SPANISH_USERNAME, Language.ES);
    }

    // ==================== GenerationRequest Builders ====================

    /**
     * Creates a GenerationRequest with default language (EN).
     */
    public static GenerationRequest generationRequest(int count) {
        return GenerationRequest.of(Language.EN, count);
    }

    /**
     * Creates a GenerationRequest with specified language.
     */
    public static GenerationRequest generationRequest(Language language, int count) {
        return GenerationRequest.of(language, count);
    }

    // ==================== GenerationResponse Builders ====================

    /**
     * Creates a GenerationResponse with normal (non-cached) response time.
     */
    public static GenerationResponse generationResponse(List<Username> usernames, Language language) {
        return GenerationResponse.of(usernames, language, false, 50L);
    }

    /**
     * Creates a GenerationResponse with custom response time.
     */
    public static GenerationResponse generationResponse(
            List<Username> usernames, Language language, long responseTimeMs) {
        return GenerationResponse.of(usernames, language, false, responseTimeMs);
    }

    /**
     * Creates a GenerationResponse indicating cache hit with fast response time.
     */
    public static GenerationResponse cachedResponse(List<Username> usernames, Language language) {
        return GenerationResponse.of(usernames, language, true, 10L);
    }

    /**
     * Creates a GenerationResponse with all parameters specified.
     */
    public static GenerationResponse generationResponse(
            List<Username> usernames, Language language, boolean cacheHit, long responseTimeMs) {
        return GenerationResponse.of(usernames, language, cacheHit, responseTimeMs);
    }

    // ==================== Response Time Constants ====================

    public static final long FAST_CACHE_RESPONSE = 10L;
    public static final long NORMAL_RESPONSE_TIME = 50L;
    public static final long SLOW_RESPONSE_TIME = 150L;
}
