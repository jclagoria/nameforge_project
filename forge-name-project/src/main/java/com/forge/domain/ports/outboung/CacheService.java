package com.forge.domain.ports.outboung;

import com.forge.domain.model.Language;
import com.forge.domain.model.Username;
import com.forge.domain.model.ValidationResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Outbound port for caching operations.
 */
public interface CacheService {

    /**
     * Retrieves cached usernames for a specific language.
     *
     * @param language the language of usernames
     * @param count number of usernames to retrieve
     * @return Flux<Username> cached usernames
     */
    Flux<Username> getCachedUsernames(Language language, int count);

    /**
     * Caches a collection of usernames.
     *
     * @param language the language of usernames
     * @param usernames the usernames to cache
     * @return Mono<Void> completion signal
     */
    Mono<Void> cacheUsernames(Language language, Flux<Username> usernames);

    /**
     * Checks if username exists in Bloom filter (fast negative lookup).
     *
     * @param username the username to check
     * @return Mono<Boolean> true if might exist, false if definitely doesn't exist
     */
    Mono<Boolean> mightExist(String username);

    /**
     * Retrieves cached validation result for a username and language.
     *
     * @param username the username to validate
     * @param language the language context
     * @return Mono<ValidationResult> cached validation result, or empty if not cached
     */
    Mono<ValidationResult> getCachedValidation(String username, Language language);

    /**
     * Caches a validation result for a username and language.
     *
     * @param username the username
     * @param language the language context
     * @param result the validation result to cache
     * @return Mono<Void> completion signal
     */
    Mono<Void> cacheValidation(String username, Language language, ValidationResult result);

    /**
     * Invalidates cached validation for a specific username.
     * Useful when username is taken or rules change.
     *
     * @param username the username to invalidate
     * @return Mono<Void> completion signal
     */
    Mono<Void> invalidateValidation(String username);

    /**
     * Invalidates all generation caches across all languages.
     * Used when username availability changes (e.g., username marked as used).
     *
     * @return Mono<Void> completion signal
     */
    Mono<Void> invalidateGenerationCaches();
}
