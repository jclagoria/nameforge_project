package com.forge.domain.usecases;

import com.forge.domain.model.Language;
import com.forge.domain.model.Username;
import com.forge.domain.model.ValidationRequest;
import com.forge.domain.model.ValidationResult;
import com.forge.domain.ports.inbound.UsernameValidationUseCase;
import com.forge.domain.ports.outboung.CacheService;
import com.forge.domain.ports.outboung.ModerationService;
import com.forge.domain.ports.outboung.UsernameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsernameValidationUseCaseImpl implements UsernameValidationUseCase {

    private static final Duration MODERATION_TIMEOUT = Duration.ofSeconds(3);

    private final UsernameRepository usernameRepository;
    private final ModerationService moderationService;
    private final CacheService cacheService;

    @Override
    public Mono<ValidationResult> validate(ValidationRequest request) {

        return cacheService.getCachedValidation(request.username(), request.language())
                .switchIfEmpty(Mono.defer(() -> {
                    // Cache miss - perform full validation
                    log.debug("Cache MISS for validation: username={}, language={}",
                            request.username(), request.language());
                    return performFullValidation(request)
                            .flatMap(result -> cacheValidationResult(request, result));
                }));
    }

    private Mono<ValidationResult> validateFormat(String username, Language language) {
        Instant validatedAt = Instant.now();
        List<String> reasons = new ArrayList<>();

        try {
            Username.of(username, language);
            return Mono.just(ValidationResult.valid(username, validatedAt));
        } catch (IllegalArgumentException e) {
            reasons.add("Invalid format: " + e.getMessage());
            return Mono.just(ValidationResult.invalid(
                    username,
                    true,  // uniqueness not checked yet
                    true,  // appropriateness not checked yet
                    false, // invalid format
                    reasons,
                    validatedAt
            ));
        }
    }

    private Mono<Boolean> validateUniqueness(String username) {
        return usernameRepository.existsByUsername(username)
                .map(exists -> !exists)
                .onErrorResume(
                        e -> {
                            log.error("Error checking username uniqueness: {}", e.getMessage());
                            return Mono.just(true); // Assume unique on error
                        }
                );
    }

    private Mono<ValidationResult> validationAppropriateness(
            String username,
            Language language,
            boolean isUnique,
            Instant validatedAt
    ) {
        return moderationService.isAppropriate(username)
                .timeout(MODERATION_TIMEOUT)
                .onErrorResume(e -> {
                    log.warn("Moderation service error, assuming appropriate: {}", e.getMessage());
                    return Mono.just(true);
                })
                .map(isAppropriate -> buildValidationResult(
                        username,
                        isUnique,
                        isAppropriate,
                        validatedAt
                ));
    }

    private Mono<ValidationResult> performFullValidation(ValidationRequest request) {
        Instant validatedAt = Instant.now();

        return validateFormat(request.username(), request.language())
                .flatMap(formatResult -> {
                    if (!formatResult.isValidFormat()) {
                        return Mono.just(formatResult);
                    }

                    return validateUniqueness(request.username())
                            .flatMap(isUnique -> validationAppropriateness(
                                    request.username(),
                                    request.language(),
                                    isUnique,
                                    validatedAt)
                            );
                });
    }

    private Mono<ValidationResult> cacheValidationResult(
            ValidationRequest request,
            ValidationResult result
    ) {
        return cacheService.cacheValidation(request.username(), request.language(), result)
                .thenReturn(result)
                .onErrorResume(error -> {
                    log.warn("Failed to cache validation (continuing): {}", error.getMessage());
                    return Mono.just(result);  // Continue even if caching fails
                });
    }

    private ValidationResult buildValidationResult(
            String username,
            boolean isUnique,
            boolean isAppropriate,
            Instant validatedAt
    ) {
        List<String> reasons = new ArrayList<>();

        if (!isUnique) {
            reasons.add("Username is already taken");
        }

        if (!isAppropriate) {
            reasons.add("Content not appropriate for general use");
        }

        if (reasons.isEmpty()) {
            return ValidationResult.valid(username, validatedAt);
        }

        return ValidationResult.invalid(
                username,
                isUnique,
                isAppropriate,
                true, // format already validated
                reasons,
                validatedAt
        );
    }

}
