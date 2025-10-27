package com.forge.infrastructure.config;

import com.forge.domain.ports.inbound.UsernameMarkUsedUseCase;
import com.forge.domain.ports.outboung.CacheService;
import com.forge.domain.ports.outboung.UsernameRepository;
import com.forge.domain.usecases.UsernameMarkUsedCaseImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    public UsernameMarkUsedUseCase usernameMarkUsedUseCase(
            UsernameRepository usernameRepository,
            CacheService cacheService
    ) {
        return new UsernameMarkUsedCaseImpl(usernameRepository, cacheService);
    }

}
