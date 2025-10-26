package com.forge.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.adapters.outbound.database.RedisCacheServiceAdapter;
import com.forge.domain.model.Language;
import com.forge.domain.model.Username;
import com.forge.domain.model.ValidationResult;
import com.forge.domain.ports.outboung.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Cache configuration that provides a fallback no-op CacheService
 * when Redis is not available.
 */
@Slf4j
@Configuration
@AutoConfigureAfter({RedisAutoConfiguration.class, RedisConfig.class})
public class CacheConfig {

    /**
     * Primary Redis-backed cache service (created when ReactiveRedisTemplate is available)
     * Using lazy initialization to ensure ReactiveRedisTemplate is created first
     */
    @Bean(name = "cacheService")
    @Primary
    public CacheService cacheService(
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            ReactiveRedisTemplate<String, String> redisTemplate
    ) {
        if (redisTemplate != null) {
            log.info("Creating RedisCacheServiceAdapter bean with ReactiveRedisTemplate: {}",
                    redisTemplate.getClass().getName());
            return new RedisCacheServiceAdapter(redisTemplate, objectMapper);
        } else {
            log.warn("ReactiveRedisTemplate not available - creating NoOpCacheService");
            return new NoOpCacheService();
        }
    }

    /**
     * No-op implementation of CacheService used as fallback when Redis is unavailable.
     * All cache operations return empty results, effectively disabling caching.
     */
    @Slf4j
    static class NoOpCacheService implements CacheService {

        public NoOpCacheService() {
            log.warn("Using NoOpCacheService - caching is disabled. Redis may not be configured.");
        }

        @Override
        public Flux<Username> getCachedUsernames(Language language, int count) {
            return Flux.empty();
        }

        @Override
        public Mono<Void> cacheUsernames(Language language, Flux<Username> usernames) {
            return Mono.empty();
        }

        @Override
        public Mono<Boolean> mightExist(String username) {
            return Mono.just(false);
        }

        @Override
        public Mono<ValidationResult> getCachedValidation(String username, Language language) {
            log.info("Entro en NoOpCacheService.getCachedValidation");
            return Mono.empty();
        }

        @Override
        public Mono<Void> cacheValidation(String username, Language language, ValidationResult result) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> invalidateValidation(String username) {
            return Mono.empty();
        }
    }
}
