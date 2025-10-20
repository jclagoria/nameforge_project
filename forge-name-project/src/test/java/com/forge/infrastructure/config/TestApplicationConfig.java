package com.forge.infrastructure.config;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;

@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
        RedisAutoConfiguration.class,
        RedisReactiveAutoConfiguration.class
})
public class TestApplicationConfig {
    // Minimal Spring Boot configuration for tests
}
