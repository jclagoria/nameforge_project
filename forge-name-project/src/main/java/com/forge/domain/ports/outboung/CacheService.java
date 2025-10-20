package com.forge.domain.ports.outboung;

import com.forge.domain.model.Language;
import com.forge.domain.model.Username;
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

}
