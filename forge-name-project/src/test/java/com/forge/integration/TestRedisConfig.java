package com.forge.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.adapters.outbound.database.RedisCacheServiceAdapter;
import com.forge.domain.ports.outboung.CacheService;
import io.lettuce.core.ClientOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Test configuration for Redis integration tests.
 * Creates Redis beans for Testcontainers environment.
 */
@TestConfiguration
public class TestRedisConfig {

    @Bean
    @Primary
    public ReactiveRedisConnectionFactory reactiveRedisConnectionFactory(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port
    ) {
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2))
                .clientOptions(ClientOptions.builder()
                        .disconnectedBehavior(ClientOptions.DisconnectedBehavior.DEFAULT)
                        .autoReconnect(false) // Don't auto-reconnect for tests
                        .build())
                .build();

        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new org.springframework.data.redis.connection.RedisStandaloneConfiguration(host, port),
                clientConfig
        );
        factory.setShareNativeConnection(false);
        factory.setValidateConnection(false); // Don't validate connection for graceful degradation
        factory.afterPropertiesSet();
        return factory;
    }

    @Bean
    @Primary
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory
    ) {
        StringRedisSerializer serializer = new StringRedisSerializer();

        RedisSerializationContext<String, String> context = RedisSerializationContext
                .<String, String>newSerializationContext(serializer)
                .key(serializer)
                .value(serializer)
                .hashKey(serializer)
                .hashValue(serializer)
                .build();

        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }

    @Bean
    public CacheService cacheService(
            ReactiveRedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper
    ) {
        return new RedisCacheServiceAdapter(redisTemplate, objectMapper);
    }
}
