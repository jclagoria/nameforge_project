package com.forge.infrastructure.config;

import com.forge.domain.model.Language;
import com.forge.domain.model.Username;
import com.forge.domain.ports.outboung.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Cache configuration that provides a fallback no-op CacheService
 * when Redis is not available.
 */
@Configuration
public class CacheConfig {

    @Bean
    @ConditionalOnMissingBean(CacheService.class)
    public CacheService noOpCacheService() {
        return new NoOpCacheService();
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
    }
}
