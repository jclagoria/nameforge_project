package com.forge.domain.ports.inbound;

import com.forge.domain.model.MarkUsedResult;
import reactor.core.publisher.Mono;

/**
 * Inbound port for marking username as used.
 * This interface defines what the application can do (driving port).
 */
public interface UsernameMarkUsedUseCase {

    /**
     * Marks a username as used (claimed by a user).
     * Updates database and invalidates cache layers.
     *
     * @param username the username to mark as used
     * @return Mono of MarkUsedResult with operation details
     */
    Mono<MarkUsedResult> markAsUsed(String username);

}
