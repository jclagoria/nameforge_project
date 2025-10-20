package com.forge.infrastructure.config;

import jakarta.validation.Validator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Test configuration that provides a Validator bean for validation support in tests.
 * CacheService fallback is now provided by CacheConfig in main.
 */
@TestConfiguration
public class TestCacheConfig {

    @Bean
    @ConditionalOnMissingBean(Validator.class)
    public Validator validator() {
        return new LocalValidatorFactoryBean();
    }
}
