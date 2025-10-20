package com.forge.domain.ports.outboung;

import com.forge.domain.model.Language;
import com.forge.domain.model.Username;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Outbound port for username persistence.
 * This interface defines what the application needs from persistence layer.
 */
public interface UsernameRepository {

    /**
     * Checks if a username already exists in the system.
     *
     * @param username the username to check
     * @return Mono<Boolean> true if exists, false otherwise
     */
    Mono<Boolean> existsByUsername(String username);

    /**
     * Saves a single username to the repository.
     *
     * @param username the username to save
     * @return Mono<Username> the saved username
     */
    Mono<Username> save(Username username);

    /**
     * Saves a batch of usernames.
     *
     * @param usernames the flux of usernames to save
     * @return Flux<Username> the saved usernames
     */
    Flux<Username> saveBatch(Flux<Username> usernames);

    /**
     * Finds available (unused) usernames by language.
     *
     * @param language the language filter
     * @param limit maximum number of usernames to retrieve
     * @return Flux<Username> available usernames
     */
    Flux<Username> findAvailableByLanguage(Language language, int limit);

}
