package com.forge.adapters.outbound.database;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.domain.model.Language;
import com.forge.domain.model.PatternType;
import com.forge.domain.model.Username;
import com.forge.domain.model.ValidationResult;
import com.forge.domain.ports.outboung.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Redis-backed implementation of CacheService using ReactiveRedisTemplate.
 * Configured as a bean in CacheConfig when Redis is available.
 */
@Slf4j
@RequiredArgsConstructor
public class RedisCacheServiceAdapter implements CacheService {

    private static final String USERNAME_CACHE_PREFIX = "username:";
    private static final String BLOOM_FILER_PREFIX = "bloom:usernames";
    private static final String VALIDATION_CACHE_PREFIX = "validation:usernames";
    private static final String CACHE_VERSION = "V1";

    private static final Duration CACHE_TTL =  Duration.ofMinutes(30);
    private static final Duration VALIDATION_CACHE_TTL =  Duration.ofMinutes(60);

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Flux<Username> getCachedUsernames(Language language, int count) {
        String key = USERNAME_CACHE_PREFIX + language.name();

        return Flux.defer(() -> redisTemplate.opsForList()
                        .range(key, 0, count - 1)
                        .flatMap(this::deserializeUsername)
                        .take(count))
                .onErrorResume(error -> {
                    // Gracefully handle Redis failures - return empty cache
                    return Flux.empty();
                });
    }

    @Override
    public Mono<Void> cacheUsernames(Language language, Flux<Username> usernames) {
        String  key = USERNAME_CACHE_PREFIX + language.name();

        return usernames
                .collectList()
                .flatMap(usernameList -> {
                    if (usernameList.isEmpty()) {
                        return Mono.empty();
                    }

                    // Serialize usernames for list cache
                    Flux<String> serializedFlux = Flux.fromIterable(usernameList)
                            .flatMap(this::serializeUsername);

                    // Extract username values for Bloom filter
                    String[] usernameValues = usernameList.stream()
                            .map(Username::value)
                            .toArray(String[]::new);

                    return serializedFlux
                            .collectList()
                            .flatMap(serialized -> {
                                // Save to list cache
                                Mono<Long> listOp = redisTemplate.opsForList()
                                        .rightPushAll(key, serialized);

                                // Add to Bloom filter (Set)
                                Mono<Long> bloomOp = redisTemplate.opsForSet()
                                        .add(BLOOM_FILER_PREFIX, usernameValues);

                                // Set expiration on list cache
                                Mono<Boolean> expireOp = redisTemplate.expire(key, CACHE_TTL);

                                return Mono.when(listOp, bloomOp, expireOp);
                            });
                })
                .onErrorResume(error -> {
                    // Gracefully handle Redis failures - continue without caching
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Boolean> mightExist(String username) {
        return Mono.defer(() -> redisTemplate.opsForSet()
                        .isMember(BLOOM_FILER_PREFIX, username)
                        .defaultIfEmpty(false))
                .onErrorReturn(false); // Gracefully handle Redis failures - assume not exists
    }

    @Override
    public Mono<ValidationResult> getCachedValidation(String username, Language language) {
        log.info("entro en RedisCacheServiceAdapter.getCachedValidation");

        String key = buildValidationCacheKey(username, language);

        return Mono.defer(() -> redisTemplate.opsForValue()
                .get(key)
                .flatMap(this::deserializeValidationResult)
                .doOnNext(result ->  log.debug(
                        "Cache HIT for validation: username={}, language={}",
                        username, language
                ))
                .doOnError(error -> log.warn(
                        "Error retrieving cached validation: {}",
                        error.getMessage()
                )))
                .onErrorResume(error -> {
                    log.warn("Cache retrieval failed, will perform full validation: {}",
                            error.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> cacheValidation(String username, Language language, ValidationResult result) {
        log.info("entro en RedisCacheServiceAdapter.cacheValidation");
        String key = buildValidationCacheKey(username, language);

        return Mono.defer(() -> serializeValidationResult(result)
                .flatMap(serialized -> redisTemplate.opsForValue()
                        .set(key, serialized, VALIDATION_CACHE_TTL)
                        .doOnSuccess(success -> log.debug(
                                "Cached validation result: username={}, language={}, isValid={}",
                                username, language, result.isValid()
                        ))
                        .then())
                .doOnError(error -> log.warn(
                        "Failed to cache validation result: {}",
                        error.getMessage()
                )))
                .onErrorResume(error -> {
                    // Gracefully handle cache failures - don't fail the validation
                    log.warn("Validation caching failed (continuing): {}", error.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> invalidateValidation(String username) {
        log.debug("Invalidating validation cache for username: {}", username);

        // Create deletion operations for all language-specific validation caches
        List<Mono<Long>> validationDeletions = Arrays.stream(Language.values())
                .map(language -> {
                    String key = buildValidationCacheKey(username, language);
                    return redisTemplate.delete(key);
                })
                .toList();

        // Create Bloom filter removal operation
        Mono<Long> bloomFilterRemoval = redisTemplate.opsForSet()
                .remove(BLOOM_FILER_PREFIX, username)
                .doOnSuccess(removed -> {
                    if (removed != null && removed > 0) {
                        log.debug("Removed username '{}' from Bloom filter", username);
                    }
                });

        // Execute all deletions in parallel
        return Mono.when(validationDeletions)
                .then(bloomFilterRemoval)
                .then()
                .doOnSuccess(v -> log.debug(
                        "Successfully invalidated validation cache and Bloom filter for username: {}",
                        username
                ))
                .doOnError(error -> log.warn(
                        "Failed to fully invalidate cache for username '{}': {}",
                        username, error.getMessage()
                ))
                .onErrorResume(error -> {
                    // Gracefully handle cache failures - don't fail the operation
                    log.warn("Cache invalidation failed (continuing): {}", error.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<String> serializeUsername(Username username) {
        try {
            CachedUsernameDto dto = new CachedUsernameDto(
                    username.value(),
                    username.language().name(),
                    username.patternType().name()
            );

            return Mono.just(objectMapper.writeValueAsString(dto));
        } catch (JsonProcessingException ex) {
            return Mono.error(new RuntimeException("Failed to serialize username", ex));
        }
    }

    private Mono<Username> deserializeUsername(String json) {
        try {
            CachedUsernameDto dto = objectMapper.readValue(json, CachedUsernameDto.class);
            return Mono.just(
                    Username.of(
                            dto.value(),
                            Language.valueOf(dto.language()),
                            PatternType.valueOf(dto.patternType())
                    )
            );
        } catch (JsonProcessingException ex) {
            return Mono.error(new RuntimeException("Failed to deserialize username", ex));
        }
    }

    private String buildValidationCacheKey(String username, Language language) {
        return String.format("%s%s:%s:%s",
                VALIDATION_CACHE_PREFIX,
                username.toLowerCase(),
                language.name().toLowerCase(),
                CACHE_VERSION
        );
    }

    private Mono<String> serializeValidationResult(ValidationResult result) {
        try {
            CachedValidationDto dto = new CachedValidationDto(
                    result.username(),
                    result.isValid(),
                    result.isUnique(),
                    result.isAppropriate(),
                    result.isValidFormat(),
                    result.reasons(),
                    result.confidenceScore(),
                    result.validatedAt().toString()
            );

            return Mono.just(objectMapper.writeValueAsString(dto));
        } catch (JsonProcessingException ex) {
            return Mono.error(new RuntimeException(
                    "Failed to serialize validation result", ex));
        }
    }

    private Mono<ValidationResult> deserializeValidationResult(String json) {
        try {
            CachedValidationDto dto = objectMapper.readValue(json, CachedValidationDto.class);

            return Mono.just(new ValidationResult(
                    dto.username(),
                    dto.isValid,
                    dto.isUnique,
                    dto.isAppropriate,
                    dto.isValidFormat,
                    dto.reasons(),
                    dto.confidenceScore(),
                    Instant.parse(dto.validatedAt())
            ));
        } catch (Exception ex) {
            return Mono.error(new RuntimeException(
                    "Failed to deserialize validation result", ex));
        }
    }

    private record CachedUsernameDto(String value, String language, String patternType) {}

    private record CachedValidationDto(
            String username,
            boolean isValid,
            boolean isUnique,
            boolean isAppropriate,
            boolean isValidFormat,
            List<String> reasons,
            double confidenceScore,
            String validatedAt
    ) {}
}
