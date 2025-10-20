package com.forge.domain.ports.outboung;

import reactor.core.publisher.Mono;

/**
 * Outbound port for content moderation.
 */
public interface ModerationService {

    /**
     * Checks if a username is appropriate (no offensive content).
     *
     * @param username the username to check
     * @return Mono<Boolean> true if appropriate, false otherwise
     */
    Mono<Boolean> isAppropriate(String username);

}
