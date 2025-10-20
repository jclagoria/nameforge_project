package com.forge.adapters.outbound.generation;

import com.forge.domain.model.Language;
import com.forge.domain.model.PatternType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DataFakerGeneratorService Tests")
class DataFakerGeneratorServiceTest {

    private DataFakerGeneratorService generatorService;

    @BeforeEach
    void setUp() {
        generatorService = new DataFakerGeneratorService();
    }

    @Test
    @DisplayName("Should Generate Classic Username In English")
    void shouldGenerateClassicUsernameInEnglish() {
        // ACT & ASSERT
        StepVerifier.create(generatorService.generateUsername(Language.EN, PatternType.CLASSIC))
                .assertNext(username -> {
                    assertThat(username).isNotNull();
                    assertThat(username).isNotEmpty();
                    assertThat(username.length()).isGreaterThan(5);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Generate Classic Username In Spanish")
    void shouldGenerateClassicUsernameInSpanish() {
        // ACT & ASSERT
        StepVerifier.create(generatorService.generateUsername(Language.ES, PatternType.CLASSIC))
                .assertNext(username -> {
                    assertThat(username).isNotNull();
                    assertThat(username).isNotEmpty();
                    assertThat(username.length()).isGreaterThan(5);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Generate Separator Username")
    void shouldGenerateSeparatorUsername() {
        // ACT & ASSERT
        StepVerifier.create(generatorService.generateUsername(Language.EN, PatternType.SEPARATOR))
                .assertNext(username -> {
                    assertThat(username).isNotNull();
                    assertThat(username).contains("_");
                    assertThat(username.split("_")).hasSizeGreaterThanOrEqualTo(2);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Generate Wordplay Username")
    void shouldGenerateWordplayUsername() {
        // ACT & ASSERT
        StepVerifier.create(generatorService.generateUsername(Language.EN, PatternType.WORDPLAY))
                .assertNext(username -> {
                    assertThat(username).isNotNull();
                    assertThat(username).isNotEmpty();
                    assertThat(username).matches(".*\\d+$"); // Should end with digits
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Generate Different Usernames On Multiple Calls")
    void shouldGenerateDifferentUsernamesOnMultipleCalls() {
        // ARRANGE
        String username1 = generatorService.generateUsername(Language.EN, PatternType.CLASSIC).block();
        String username2 = generatorService.generateUsername(Language.EN, PatternType.CLASSIC).block();
        String username3 = generatorService.generateUsername(Language.EN, PatternType.CLASSIC).block();

        // ASSERT - All should be different (very high probability)
        assertThat(username1).isNotEqualTo(username2);
        assertThat(username2).isNotEqualTo(username3);
        assertThat(username1).isNotEqualTo(username3);
    }

    @Test
    @DisplayName("Should Support All Languages From Enum")
    void shouldSupportAllLanguagesFromEnum() {
        // ACT & ASSERT - All languages should work
        for (Language language : Language.values()) {
            StepVerifier.create(generatorService.generateUsername(language, PatternType.CLASSIC))
                    .assertNext(username -> {
                        assertThat(username).isNotNull();
                        assertThat(username).isNotEmpty();
                    })
                    .verifyComplete();
        }
    }

    @Test
    @DisplayName("Should Support All Pattern Types")
    void shouldSupportAllPatternTypes() {
        // ACT & ASSERT - All pattern types should work
        for (PatternType patternType : PatternType.values()) {
            StepVerifier.create(generatorService.generateUsername(Language.EN, patternType))
                    .assertNext(username -> {
                        assertThat(username).isNotNull();
                        assertThat(username).isNotEmpty();
                    })
                    .verifyComplete();
        }
    }
}
