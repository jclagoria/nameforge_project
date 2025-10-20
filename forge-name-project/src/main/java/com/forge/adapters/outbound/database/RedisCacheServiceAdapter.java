package com.forge.adapters.outbound.database;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.domain.model.Language;
import com.forge.domain.model.PatternType;
import com.forge.domain.model.Username;
import com.forge.domain.ports.outboung.CacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@ConditionalOnBean(ReactiveRedisTemplate.class)
@ConditionalOnProperty(prefix = "spring.data.redis.repositories", name = "enabled", matchIfMissing = true)
public class RedisCacheServiceAdapter implements CacheService {

    private static final String USERNAME_CACHE_PREFIX = "username:";
    private static final String BLOOM_FILER_PREFIX = "bloom:usernames";
    private static final Duration CACHE_TTL =  Duration.ofMinutes(30);

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Flux<Username> getCachedUsernames(Language language, int count) {
        String key = USERNAME_CACHE_PREFIX + language.name();

        return redisTemplate.opsForList()
                .range(key, 0, count - 1)
                .flatMap(this::deserializeUsername)
                .take(count);
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
                });
    }

    @Override
    public Mono<Boolean> mightExist(String username) {
        return redisTemplate.opsForSet()
                .isMember(BLOOM_FILER_PREFIX, username)
                .defaultIfEmpty(false);
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

    private record CachedUsernameDto(String value, String language, String patternType) {}
}
