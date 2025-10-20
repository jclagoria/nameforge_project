package com.forge.adapters.outbound.generation;

import com.forge.domain.model.PatternType;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UsernamePatternGenerator Tests")
class UsernamePatternGeneratorTest {

    private Faker faker;
    private Random random;

    @BeforeEach
    void setUp() {
        faker = new Faker();
        random = new Random();
    }

    @Test
    @DisplayName("Should Generate Classic Pattern With Correct Format")
    void shouldGenerateClassicPatternWithCorrectFormat() {
        // ACT
        String username = UsernamePatternGenerator.generateClassic(faker, random);

        // ASSERT
        assertThat(username).isNotNull();
        assertThat(username).isNotEmpty();
        assertThat(username).matches("^[a-z]+[a-z]+\\d+$"); // letters + letters + digits
        assertThat(username).doesNotContain(" ", "_", "-");
    }

    @Test
    @DisplayName("Should Generate Classic Pattern With Number Between 0 And 99")
    void shouldGenerateClassicPatternWithNumberBetween0And99() {
        // ACT
        String username = UsernamePatternGenerator.generateClassic(faker, random);

        // ASSERT - Extract number at the end
        String numberPart = username.replaceAll("^[a-z]+", "");
        int number = Integer.parseInt(numberPart);
        assertThat(number).isBetween(0, 99);
    }

    @Test
    @DisplayName("Should Generate Different Classic Usernames On Multiple Calls")
    void shouldGenerateDifferentClassicUsernamesOnMultipleCalls() {
        // ARRANGE
        Set<String> usernames = new HashSet<>();

        // ACT - Generate 10 usernames
        for (int i = 0; i < 10; i++) {
            usernames.add(UsernamePatternGenerator.generateClassic(faker, random));
        }

        // ASSERT - All should be different (very high probability)
        assertThat(usernames).hasSizeGreaterThan(7); // At least 80% unique
    }

    @Test
    @DisplayName("Should Generate Separator Pattern With Correct Format")
    void shouldGenerateSeparatorPatternWithCorrectFormat() {
        // ACT
        String username = UsernamePatternGenerator.generateSeparator(faker, random);

        // ASSERT
        assertThat(username).isNotNull();
        assertThat(username).isNotEmpty();
        assertThat(username).matches("^[a-z]+_[a-z]+_\\d+$"); // word_word_number
        assertThat(username).doesNotContain(" ", "-");

        // Verify it has exactly 2 underscores
        long underscoreCount = username.chars().filter(ch -> ch == '_').count();
        assertThat(underscoreCount).isEqualTo(2);
    }

    @Test
    @DisplayName("Should Generate Separator Pattern With Three Parts")
    void shouldGenerateSeparatorPatternWithThreeParts() {
        // ACT
        String username = UsernamePatternGenerator.generateSeparator(faker, random);

        // ASSERT
        String[] parts = username.split("_");
        assertThat(parts).hasSize(3);
        assertThat(parts[0]).matches("[a-z]+"); // First word
        assertThat(parts[1]).matches("[a-z]+"); // Second word
        assertThat(parts[2]).matches("\\d+");   // Number
    }

    @Test
    @DisplayName("Should Generate Separator Pattern With Number Between 0 And 99")
    void shouldGenerateSeparatorPatternWithNumberBetween0And99() {
        // ACT
        String username = UsernamePatternGenerator.generateSeparator(faker, random);

        // ASSERT - Extract number after last underscore
        String[] parts = username.split("_");
        int number = Integer.parseInt(parts[2]);
        assertThat(number).isBetween(0, 99);
    }

    @Test
    @DisplayName("Should Generate Different Separator Usernames On Multiple Calls")
    void shouldGenerateDifferentSeparatorUsernamesOnMultipleCalls() {
        // ARRANGE
        Set<String> usernames = new HashSet<>();

        // ACT - Generate 10 usernames
        for (int i = 0; i < 10; i++) {
            usernames.add(UsernamePatternGenerator.generateSeparator(faker, random));
        }

        // ASSERT - All should be different (very high probability)
        assertThat(usernames).hasSizeGreaterThan(7); // At least 80% unique
    }

    @Test
    @DisplayName("Should Generate Wordplay Pattern With Correct Format")
    void shouldGenerateWordplayPatternWithCorrectFormat() {
        // ACT
        String username = UsernamePatternGenerator.generateWordplay(faker, random);

        // ASSERT
        assertThat(username).isNotNull();
        assertThat(username).isNotEmpty();
        assertThat(username).matches("^[a-z]+[a-z]+\\d+$"); // verb + noun + digits
        assertThat(username).doesNotContain(" ", "_", "-");
    }

    @Test
    @DisplayName("Should Generate Wordplay Pattern With Number Between 0 And 99")
    void shouldGenerateWordplayPatternWithNumberBetween0And99() {
        // ACT
        String username = UsernamePatternGenerator.generateWordplay(faker, random);

        // ASSERT - Extract number at the end
        String numberPart = username.replaceAll("^[a-z]+", "");
        int number = Integer.parseInt(numberPart);
        assertThat(number).isBetween(0, 99);
    }

    @Test
    @DisplayName("Should Generate Different Wordplay Usernames On Multiple Calls")
    void shouldGenerateDifferentWordplayUsernamesOnMultipleCalls() {
        // ARRANGE
        Set<String> usernames = new HashSet<>();

        // ACT - Generate 10 usernames
        for (int i = 0; i < 10; i++) {
            usernames.add(UsernamePatternGenerator.generateWordplay(faker, random));
        }

        // ASSERT - All should be different (very high probability)
        assertThat(usernames).hasSizeGreaterThan(7); // At least 80% unique
    }

    @Test
    @DisplayName("Should Generate Classic Pattern Via Main Generate Method")
    void shouldGenerateClassicPatternViaMainGenerateMethod() {
        // ACT
        String username = UsernamePatternGenerator.generate(PatternType.CLASSIC, faker, random);

        // ASSERT
        assertThat(username).isNotNull();
        assertThat(username).matches("^[a-z]+[a-z]+\\d+$");
        assertThat(username).doesNotContain(" ", "_", "-");
    }

    @Test
    @DisplayName("Should Generate Separator Pattern Via Main Generate Method")
    void shouldGenerateSeparatorPatternViaMainGenerateMethod() {
        // ACT
        String username = UsernamePatternGenerator.generate(PatternType.SEPARATOR, faker, random);

        // ASSERT
        assertThat(username).isNotNull();
        assertThat(username).matches("^[a-z]+_[a-z]+_\\d+$");
        long underscoreCount = username.chars().filter(ch -> ch == '_').count();
        assertThat(underscoreCount).isEqualTo(2);
    }

    @Test
    @DisplayName("Should Generate Wordplay Pattern Via Main Generate Method")
    void shouldGenerateWordplayPatternViaMainGenerateMethod() {
        // ACT
        String username = UsernamePatternGenerator.generate(PatternType.WORDPLAY, faker, random);

        // ASSERT
        assertThat(username).isNotNull();
        assertThat(username).matches("^[a-z]+[a-z]+\\d+$");
        assertThat(username).doesNotContain(" ", "_", "-");
    }

    @Test
    @DisplayName("Should Generate All Pattern Types Successfully")
    void shouldGenerateAllPatternTypesSuccessfully() {
        // ACT & ASSERT - All pattern types should work
        for (PatternType patternType : PatternType.values()) {
            String username = UsernamePatternGenerator.generate(patternType, faker, random);

            assertThat(username).isNotNull();
            assertThat(username).isNotEmpty();
            assertThat(username.length()).isGreaterThan(5);
        }
    }

    @Test
    @DisplayName("Should Generate Usernames Without Spaces")
    void shouldGenerateUsernamesWithoutSpaces() {
        // ACT - Test all patterns
        String classic = UsernamePatternGenerator.generateClassic(faker, random);
        String separator = UsernamePatternGenerator.generateSeparator(faker, random);
        String wordplay = UsernamePatternGenerator.generateWordplay(faker, random);

        // ASSERT - None should have spaces
        assertThat(classic).doesNotContain(" ");
        assertThat(separator).doesNotContain(" ");
        assertThat(wordplay).doesNotContain(" ");
    }

    @Test
    @DisplayName("Should Generate Lowercase Usernames Only")
    void shouldGenerateLowercaseUsernamesOnly() {
        // ACT - Test all patterns
        String classic = UsernamePatternGenerator.generateClassic(faker, random);
        String separator = UsernamePatternGenerator.generateSeparator(faker, random);
        String wordplay = UsernamePatternGenerator.generateWordplay(faker, random);

        // ASSERT - All should be lowercase (except numbers)
        assertThat(classic).matches("^[a-z0-9]+$");
        assertThat(separator).matches("^[a-z0-9_]+$");
        assertThat(wordplay).matches("^[a-z0-9]+$");
    }

    @Test
    @DisplayName("Should Generate Usernames With Minimum Length Of 5 Characters")
    void shouldGenerateUsernamesWithMinimumLengthOf5Characters() {
        // ACT - Generate multiple usernames
        for (int i = 0; i < 20; i++) {
            String classic = UsernamePatternGenerator.generateClassic(faker, random);
            String separator = UsernamePatternGenerator.generateSeparator(faker, random);
            String wordplay = UsernamePatternGenerator.generateWordplay(faker, random);

            // ASSERT - All should be at least 5 characters
            assertThat(classic.length()).isGreaterThanOrEqualTo(5);
            assertThat(separator.length()).isGreaterThanOrEqualTo(5);
            assertThat(wordplay.length()).isGreaterThanOrEqualTo(5);
        }
    }

    @Test
    @DisplayName("Should Generate Usernames Ending With Numbers")
    void shouldGenerateUsernamesEndingWithNumbers() {
        // ACT
        String classic = UsernamePatternGenerator.generateClassic(faker, random);
        String separator = UsernamePatternGenerator.generateSeparator(faker, random);
        String wordplay = UsernamePatternGenerator.generateWordplay(faker, random);

        // ASSERT - All should end with digits
        assertThat(classic).matches(".*\\d+$");
        assertThat(separator).matches(".*\\d+$");
        assertThat(wordplay).matches(".*\\d+$");
    }

    @Test
    @DisplayName("Should Handle Edge Case With Single Digit Numbers")
    void shouldHandleEdgeCaseWithSingleDigitNumbers() {
        // ARRANGE - Use fixed Random to test specific cases
        Random fixedRandom = new Random(42); // Seed for reproducibility

        // ACT
        String username = UsernamePatternGenerator.generateClassic(faker, fixedRandom);

        // ASSERT - Should handle single digit (0-9) correctly
        assertThat(username).isNotNull();
        assertThat(username).matches("^[a-z]+[a-z]+\\d{1,2}$"); // 1 or 2 digits
    }

    @Test
    @DisplayName("Should Generate Consistent Results With Same Random Seed")
    void shouldGenerateConsistentResultsWithSameRandomSeed() {
        // ARRANGE
        Random random1 = new Random(123);
        Random random2 = new Random(123);
        Faker faker1 = new Faker(random1);
        Faker faker2 = new Faker(random2);

        // ACT
        String username1 = UsernamePatternGenerator.generateClassic(faker1, random1);
        String username2 = UsernamePatternGenerator.generateClassic(faker2, random2);

        // ASSERT - Same seed should produce same result
        assertThat(username1).isEqualTo(username2);
    }
}
