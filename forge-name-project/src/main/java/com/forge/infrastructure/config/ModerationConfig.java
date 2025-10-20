package com.forge.infrastructure.config;

import com.forge.infrastructure.properties.ModerationProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(ModerationProperties.class)
public class ModerationConfig {

    /**
     * WebClient bean for moderation services (OpenAI and Perspective API).
     * Uses the longest timeout between both services.
     */
    @Bean
    public WebClient moderationWebClient(ModerationProperties properties) {
        // Use the longest timeout between OpenAI and Perspective
        Duration timeout = properties.getPerspective().getTimeout()
                .compareTo(properties.getOpenai().getTimeout()) > 0
                ? properties.getPerspective().getTimeout()
                : properties.getOpenai().getTimeout();

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(timeout);

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Bean
    public CircuitBreakerRegistry moderationCircuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();

        return CircuitBreakerRegistry.of(config);
    }

}
