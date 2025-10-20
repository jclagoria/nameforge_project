package com.forge.domain.ports.inbound;

import com.forge.domain.model.GenerationRequest;
import com.forge.domain.model.GenerationResponse;
import reactor.core.publisher.Mono;

/**
 * Inbound port for username generation use case.
 * This interface defines what the application can do (driving port).
 */
public interface UsernameGenerationUseCase {

    /**
     * Generates usernames based on the provided request.
     *
     * @param request the generation request containing language and count
     * @return a Mono of GenerationResponse with generated usernames
     */
    Mono<GenerationResponse> generate(GenerationRequest request);

}
