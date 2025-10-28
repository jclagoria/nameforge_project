package com.forge.domain.usecases;

import com.forge.domain.model.MarkUsedResult;
import com.forge.domain.ports.inbound.UsernameMarkUsedUseCase;
import com.forge.domain.ports.outboung.CacheService;
import com.forge.domain.ports.outboung.UsernameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Use case implementation for marking usernames as used.
 * Coordinates database update and cache invalidation.
 */
@Slf4j
@RequiredArgsConstructor
public class UsernameMarkUsedCaseImpl implements UsernameMarkUsedUseCase {

    private final UsernameRepository usernameRepository;
    private final CacheService cacheService;

    @Override
    public Mono<MarkUsedResult> markAsUsed(String username) {
        log.info("Marking username as used: {}", username);

        return usernameRepository.markAsUsed(username)
                .flatMap(wasUpdate -> {
                    if (!wasUpdate) {
                        log.info("Username already used or not found: {}", username);
                        return Mono.just(MarkUsedResult.alreadyUsed(username));
                    }

                    // Invalidate both validation cache (specific username) and generation caches (all languages)
                    return Mono.when(
                            cacheService.invalidateValidation(username),
                            cacheService.invalidateGenerationCaches()
                    )
                            .doOnSuccess(v -> log.info("Validation and generation caches invalidated for: {}", username))
                            .thenReturn(MarkUsedResult.marked(username, LocalDateTime.now()));
                })
                .doOnError(error -> log.error("Error marking username as used: {}", username, error));
    }
}
