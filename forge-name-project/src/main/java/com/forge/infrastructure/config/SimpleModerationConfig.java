package com.forge.infrastructure.config;

import com.forge.adapters.outbound.moderation.SimpleModerationService;
import com.forge.domain.ports.outboung.ModerationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for SimpleModerationService as a fallback bean.
 * <p>
 * This bean is created ONLY when no other ModerationService implementation is available.
 * It serves as the default moderation service when:
 * <ul>
 *   <li>OpenAI moderation is disabled (moderation.openai.enabled=false)</li>
 *   <li>Perspective API is disabled (moderation.perspective.enabled=false)</li>
 *   <li>Both are disabled or not configured</li>
 * </ul>
 * </p>
 */
@Configuration
public class SimpleModerationConfig {

    /**
     * Creates SimpleModerationService bean only if no other ModerationService bean exists.
     * This ensures there's always a moderation service available for the system.
     */
    @Bean
    @ConditionalOnMissingBean(ModerationService.class)
    public ModerationService simpleModerationService() {
        return new SimpleModerationService();
    }
}
