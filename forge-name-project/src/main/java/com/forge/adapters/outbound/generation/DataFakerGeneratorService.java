package com.forge.adapters.outbound.generation;

import com.forge.domain.model.Language;
import com.forge.domain.model.PatternType;
import com.forge.domain.ports.outboung.GeneratorService;
import net.datafaker.Faker;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Service for generating usernames using DataFaker library.
 * Supports multiple languages and pattern types.
 */
@Service
public class DataFakerGeneratorService implements GeneratorService {

    private final Map<Language, Faker> fakersByLanguage;
    private final Random random;

    public DataFakerGeneratorService() {
        // Auto-initialize Fakers for all supported languages
        this.fakersByLanguage = Arrays.stream(Language.values())
                .collect(Collectors.toUnmodifiableMap(
                        lang -> lang,
                        lang -> new Faker(lang.getLocale())
                ));
        this.random = new Random();
    }

    @Override
    public Mono<String> generateUsername(Language language, PatternType patternType) {
        Faker faker = fakersByLanguage.get(language);
        if (faker == null) {
            return Mono.error(new IllegalArgumentException("Unsupported language: " + language));
        }

        // Delegate to static utility class for generation
        String username = UsernamePatternGenerator.generate(patternType, faker, random);
        return Mono.just(username);
    }
}
