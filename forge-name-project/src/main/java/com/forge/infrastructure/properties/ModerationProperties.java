package com.forge.infrastructure.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "moderation")
public class ModerationProperties {

    private OpenAiProperties openai = new OpenAiProperties();
    private PerspectiveProperties perspective = new PerspectiveProperties();

    @Data
    public static class OpenAiProperties {
        private String apiKey;
        private String endpoint = "https://api.openai.com/v1/moderations";
        private Duration timeout = Duration.ofSeconds(10);
        private boolean enabled = false;
        private int maxRetries = 2;
    }

    @Data
    public static class PerspectiveProperties {
        private String apiKey;
        private String endpoint = "https://commentanalyzer.googleapis.com/v1alpha1/comments:analyze";
        private Duration timeout = Duration.ofSeconds(10);
        private boolean enabled = true;
        private int maxRetries = 2;
        private String defaultLanguage = "es";
        private boolean doNotStore = true;
        private Thresholds thresholds = new Thresholds();

        @Data
        public static class Thresholds {
            private Double toxicity = 0.7;
            private Double severeToxicity = 0.5;
            private Double identityAttack = 0.6;
            private Double insult = 0.7;
            private Double threat = 0.5;
        }
    }

}
