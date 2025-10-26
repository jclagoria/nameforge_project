package com.forge.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Slf4j
@Configuration
public class RedisConfig {

    @Bean
    @Primary
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(
            RedisConnectionFactory connectionFactory
    ) {
        log.info("Creating reactiveRedisTemplate bean with connection factory: {}", connectionFactory.getClass().getName());
        StringRedisSerializer serializer = new StringRedisSerializer();

        RedisSerializationContext<String, String> context = RedisSerializationContext
                .<String, String>newSerializationContext(serializer)
                .key(serializer)
                .value(serializer)
                .hashKey(serializer)
                .hashValue(serializer)
                .build();

        // LettuceConnectionFactory implements both RedisConnectionFactory and ReactiveRedisConnectionFactory
        // Cast is safe because Lettuce is the only Redis client in use
        ReactiveRedisConnectionFactory reactiveFactory = (ReactiveRedisConnectionFactory) connectionFactory;
        ReactiveRedisTemplate<String, String> template = new ReactiveRedisTemplate<>(reactiveFactory, context);
        log.info("Successfully created reactiveRedisTemplate bean");
        return template;
    }
}
