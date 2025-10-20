package com.forge.domain.ports.outboung;

import com.forge.domain.model.Language;
import com.forge.domain.model.PatternType;
import reactor.core.publisher.Mono;

/**
 * Outbound port for username generation algorithms.
 */
public interface GeneratorService {

    /**
     * Generates a single username using specified pattern.
     *
     * @param language the language for generation
     * @param patternType the pattern type to use
     * @return Mono<String> the generated username string
     */
   Mono<String> generateUsername(Language language, PatternType patternType);

}
