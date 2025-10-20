package com.forge.domain.usecases;

import com.forge.domain.model.*;
import com.forge.domain.ports.inbound.UsernameGenerationUseCase;
import com.forge.domain.ports.outboung.CacheService;
import com.forge.domain.ports.outboung.GeneratorService;
import com.forge.domain.ports.outboung.ModerationService;
import com.forge.domain.ports.outboung.UsernameRepository;
import io.netty.handler.timeout.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UsernameGenerationUseCaseImpl implements UsernameGenerationUseCase {

    private static final int GENERATION_MULTIPLIER = 2;  // Generate extra to account for filtering
    private static final Duration GENERATOR_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration MODERATION_TIMEOUT = Duration.ofSeconds(3);
    private static final int MAX_RETRIES = 3;

    private final UsernameRepository usernameRepository;
    private final CacheService cacheService;
    private final GeneratorService generatorService;
    private final ModerationService moderationService;

    @Override
    public Mono<GenerationResponse> generate(GenerationRequest request) {
        long startTime = System.currentTimeMillis();

        return tryGetFromCache(request)
                .switchIfEmpty(generateNewUsername(request, startTime));
    }

    private Mono<GenerationResponse> tryGetFromCache(GenerationRequest request) {
        long startTime = System.currentTimeMillis();

        return cacheService.getCachedUsernames(request.language(), request.count())
                .collectList().filter(list -> list.size() == request.count())
                .map(usernames ->
                        createResponse(usernames, request.language(), true, startTime)
                );
    }

    private Mono<GenerationResponse> generateNewUsername(
            GenerationRequest request,
            long startTime) {
        Flux<Username> usernames = Flux.range(0, request.count() * GENERATION_MULTIPLIER)
                .flatMap(i -> generateSingleUsername(request.language()))
                .take(request.count());

        return usernames.collectList()
                .flatMap(this::saveBatch)
                .flatMap(saved -> cacheUsernames(saved, request.language())
                        .thenReturn(saved))
                .map(saved -> createResponse(saved, request.language(), false, startTime));
    }

    private Mono<Void> cacheUsernames(List<Username> usernames, Language language) {
        return cacheService.cacheUsernames(language, Flux.fromIterable(usernames));
    }

    private Mono<Username> generateSingleUsername(Language language) {
        return generatorService.generateUsername(language, PatternType.selectRandom())
                .timeout(GENERATOR_TIMEOUT)
                .onErrorResume(TimeoutException.class,
                        e -> Mono.error(new RuntimeException("Username generator Timeout", e)))
                .flatMap(value -> validateAndCreateUsername(value, language))
                .retry(MAX_RETRIES);
    }

    private Mono<Username> validateAndCreateUsername(String value, Language language) {
        return checkUniqueness(value)
                .flatMap(isUnique -> isUnique
                        ? checkAppropriateness(value, language)
                        : Mono.empty());
    }

    private Mono<Boolean> checkUniqueness(String value) {
        return cacheService.mightExist(value)
                .flatMap(mightExist -> mightExist
                        ? usernameRepository.existsByUsername(value)
                            .map(exists -> !exists)
                        : Mono.just(true));
    }

    private Mono<Username> checkAppropriateness(String value, Language language) {
        return moderationService.isAppropriate(value)
                .timeout(MODERATION_TIMEOUT)
                .onErrorResume(e -> Mono.just(true))
                .filter(appropriate -> appropriate)
                .map(appropriate -> Username.of(value, language));
    }

    private Mono<List<Username>> saveBatch(List<Username> usernames) {
        return usernameRepository.saveBatch(Flux.fromIterable(usernames)).collectList();
    }

    private GenerationResponse createResponse(
            List<Username> usernames,
            Language language,
            boolean cacheHit,
            long startTime
    ) {
        long duration = System.currentTimeMillis() - startTime;
        return GenerationResponse.of(usernames, language, cacheHit, duration);
    }

}
