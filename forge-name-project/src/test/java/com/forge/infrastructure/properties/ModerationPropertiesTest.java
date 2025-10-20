package com.forge.infrastructure.properties;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig
@EnableConfigurationProperties(ModerationProperties.class)
@TestPropertySource(properties = {
        "moderation.openai.api-key=test-key-123",
        "moderation.openai.endpoint=https://api.openai.com/v1/moderations",
        "moderation.openai.timeout=5s",
        "moderation.openai.enabled=true"
})
@DisplayName("ModerationProperties Tests")
class ModerationPropertiesTest {

    @Autowired
    private ModerationProperties properties;

    @Test
    @DisplayName("Should Load Moderation Properties")
    void shouldLoadModerationProperties() {
        // ASSERT
        assertThat(properties).isNotNull();
        assertThat(properties.getOpenai()).isNotNull();
        assertThat(properties.getOpenai().getApiKey()).isEqualTo("test-key-123");
        assertThat(properties.getOpenai().getEndpoint()).contains("moderations");
        assertThat(properties.getOpenai().getTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.getOpenai().isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Should Have Default Values")
    void shouldHaveDefaultValues() {
        // ASSERT - verify defaults are reasonable
        assertThat(properties.getOpenai().getEndpoint()).isNotBlank();
        assertThat(properties.getOpenai().getTimeout()).isGreaterThan(Duration.ZERO);
    }

}