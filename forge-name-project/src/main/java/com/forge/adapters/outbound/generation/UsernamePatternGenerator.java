package com.forge.adapters.outbound.generation;

import com.forge.domain.model.PatternType;
import net.datafaker.Faker;

import java.util.Random;

/**
 * Utility class for generating usernames based on different patterns.
 * All methods are static and stateless for maximum reusability.
 */
public final class UsernamePatternGenerator {

    private UsernamePatternGenerator() {
        // Private constructor to prevent instantiation
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Generates a username based on the specified pattern type.
     *
     * @param patternType the pattern type to use
     * @param faker the Faker instance for generating random data
     * @param random the Random instance for generating numbers
     * @return the generated username
     */
    public static String generate(PatternType patternType, Faker faker, Random random) {
        return switch (patternType) {
            case CLASSIC -> generateClassic(faker, random);
            case SEPARATOR -> generateSeparator(faker, random);
            case WORDPLAY -> generateWordplay(faker, random);
        };
    }

    /**
     * Generates a classic username: adjective + noun + number.
     * Example: "cleverpanda42"
     *
     * @param faker the Faker instance
     * @param random the Random instance
     * @return the generated classic username
     */
    static String generateClassic(Faker faker, Random random) {
        String adjective = faker.lorem().word().toLowerCase();
        String noun = faker.animal().name().toLowerCase().replaceAll("\\s+", "");
        int number = random.nextInt(100);
        return adjective + noun + number;
    }

    /**
     * Generates a separator username: word_word_number.
     * Example: "blue_tiger_77"
     *
     * @param faker the Faker instance
     * @param random the Random instance
     * @return the generated separator username
     */
    static String generateSeparator(Faker faker, Random random) {
        String firstWord = faker.color().name().toLowerCase().replaceAll("\\s+", "");
        String secondWord = faker.animal().name().toLowerCase().replaceAll("\\s+", "");
        int number = random.nextInt(100);
        return firstWord + "_" + secondWord + "_" + number;
    }

    /**
     * Generates a wordplay username: verb + noun + number.
     * Example: "codewizard99"
     *
     * @param faker the Faker instance
     * @param random the Random instance
     * @return the generated wordplay username
     */
    static String generateWordplay(Faker faker, Random random) {
        String prefix = faker.hacker().verb().toLowerCase().replaceAll("\\s+", "");
        String suffix = faker.hacker().noun().toLowerCase().replaceAll("\\s+", "");
        int number = random.nextInt(100);
        return prefix + suffix + number;
    }
}
