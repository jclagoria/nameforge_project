package com.forge.domain.ports.inbound;

import com.forge.domain.model.ValidationRequest;
import com.forge.domain.model.ValidationResult;
import reactor.core.publisher.Mono;

/**
 * Inbound port for username validation use case.
 * This interface defines what the application can do (driving port).
 */
public interface UsernameValidationUseCase {

    /**
     * Validates a username based on format, uniqueness, and appropriateness.
     *
     * @param request the validation request containing username and language
     * @return a Mono of ValidationResult with validation details
     */
    Mono<ValidationResult> validate (ValidationRequest request);

}
