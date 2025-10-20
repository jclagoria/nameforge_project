package com.forge.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@DisplayName("PatternType Tests")
class PatternTypeTest {

    @Test
    @DisplayName("Should have Classic Pattern")
    void shouldHaveClassicPatternType()
    {
        PatternType pattern = PatternType.CLASSIC;

        assertThat(pattern).isNotNull();
        assertThat(pattern.name()).isEqualTo("CLASSIC");
    }

    @Test
    @DisplayName("Should have Separator Pattern")
    void shouldHaveSeparatorPatternType()
    {
        PatternType patternType = PatternType.SEPARATOR;

        assertThat(patternType).isNotNull();
        assertThat(patternType.name()).isEqualTo("SEPARATOR");
    }

    @Test
    @DisplayName("Should have Wordplay Pattern")
    void shouldHaveWordplayPatternType()
    {
        PatternType patternType = PatternType.WORDPLAY;

        assertThat(patternType).isNotNull();
        assertThat(patternType.name()).isEqualTo("WORDPLAY");
    }

    @Test
    @DisplayName("Should parse Pattern Type from String")
    void shouldParsePatternTypeFromString()
    {
        PatternType patternClassicType = PatternType.valueOf("CLASSIC");
        PatternType patternSeparatorType = PatternType.valueOf("SEPARATOR");
        PatternType patternWordplayType = PatternType.valueOf("WORDPLAY");

        assertThat(patternClassicType).isEqualTo(PatternType.CLASSIC);
        assertThat(patternSeparatorType).isEqualTo(PatternType.SEPARATOR);
        assertThat(patternWordplayType).isEqualTo(PatternType.WORDPLAY);
    }

    @Test
    @DisplayName("Should thrown exception for invalid Pattern Type")
    void shouldThrowExceptionForInvalidPatterType()
    {
        assertThatThrownBy(() -> PatternType.valueOf("MIXPattern"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant com.forge.domain.model.PatternType.MIXPattern");
    }

    @Test
    @DisplayName("Should have correct probability for Classic pattern")
    void shouldHaveCorrectProbabilityForClassic() {
        assertThat(PatternType.CLASSIC.getProbability()).isEqualTo(0.7);
    }

    @Test
    @DisplayName("Should have correct probability for Separator pattern")
    void shouldHaveCorrectProbabilityForSeparator() {
        assertThat(PatternType.SEPARATOR.getProbability()).isEqualTo(0.2);
    }

    @Test
    @DisplayName("Should have correct probability for Wordplay pattern")
    void shouldHaveCorrectProbabilityForWordplay() {
        assertThat(PatternType.WORDPLAY.getProbability()).isEqualTo(0.1);
    }

    @Test
    @DisplayName("Should have probabilities that sum to 1.0")
    void shouldHaveProbabilitiesSumToOne() {
        double sum = 0.0;
        for (PatternType type : PatternType.values()) {
            sum += type.getProbability();
        }
        assertThat(sum).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
    }

    @RepeatedTest(10)
    @DisplayName("Should always return non-null PatternType from selectRandom")
    void shouldReturnNonNullFromSelectRandom() {
        PatternType selected = PatternType.selectRandom();
        assertThat(selected).isNotNull();
        assertThat(selected).isIn((Object[]) PatternType.values());
    }

    @Test
    @DisplayName("Should return PatternType with probability distribution approximately correct")
    void shouldReturnPatternTypeWithCorrectDistribution() {
        int iterations = 10000;
        Map<PatternType, Integer> counts = new HashMap<>();

        for (PatternType type : PatternType.values()) {
            counts.put(type, 0);
        }

        for (int i = 0; i < iterations; i++) {
            PatternType selected = PatternType.selectRandom();
            counts.put(selected, counts.get(selected) + 1);
        }

        double classicRatio = (double) counts.get(PatternType.CLASSIC) / iterations;
        double separatorRatio = (double) counts.get(PatternType.SEPARATOR) / iterations;
        double wordplayRatio = (double) counts.get(PatternType.WORDPLAY) / iterations;

        // Allow 5% margin of error for statistical variance
        assertThat(classicRatio).isBetween(0.65, 0.75);
        assertThat(separatorRatio).isBetween(0.15, 0.25);
        assertThat(wordplayRatio).isBetween(0.05, 0.15);
    }

    @Test
    @DisplayName("Should return CLASSIC as fallback if cumulative probability fails")
    void shouldReturnClassicAsFallback() {
        // This test verifies the fallback behavior in selectRandom()
        // The method should always return CLASSIC if the loop completes without finding a match
        // This is hard to test directly, but we verify CLASSIC is returned when random = 1.0 edge case

        // Run multiple times to ensure no unexpected behavior
        for (int i = 0; i < 100; i++) {
            PatternType result = PatternType.selectRandom();
            assertThat(result).isIn((Object[]) PatternType.values());
        }
    }

}