package com.forge.adapters.inbound.rest;

import com.forge.adapters.inbound.dto.GenerationRequestDto;
import com.forge.adapters.inbound.mapper.GenerationRequestMapper;
import com.forge.adapters.inbound.mapper.GenerationResponseMapper;
import com.forge.adapters.inbound.mapper.MarkUsedResponseMapper;
import com.forge.adapters.inbound.mapper.ValidationResponseMapper;
import com.forge.domain.model.Language;
import com.forge.domain.model.ValidationRequest;
import com.forge.domain.ports.inbound.UsernameGenerationUseCase;
import com.forge.domain.ports.inbound.UsernameMarkUsedUseCase;
import com.forge.domain.ports.inbound.UsernameValidationUseCase;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UsernameHandler {

    private final UsernameGenerationUseCase usernameGenerationUseCase;
    private final UsernameValidationUseCase usernameValidationUseCase;
    private final UsernameMarkUsedUseCase  usernameMarkUsedUseCase;
    private final GenerationRequestMapper requestMapper;
    private final GenerationResponseMapper responseMapper;
    private final MarkUsedResponseMapper markUsedResponseMapper;
    private final ValidationResponseMapper validationResponseMapper;
    private final Validator validator;

    public Mono<ServerResponse> generate(ServerRequest serverRequest) {
        return serverRequest.bodyToMono(GenerationRequestDto.class)
                .flatMap(this::validateRequest)
                .map(requestMapper::toDomain)
                .flatMap(usernameGenerationUseCase::generate)
                .map(responseMapper::toDto)
                .flatMap(dto -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(dto))
                .onErrorResume(IllegalArgumentException.class, error ->
                        ServerResponse.badRequest()
                                .bodyValue(error.getMessage()))
                .onErrorResume(Exception.class, error ->
                        ServerResponse.status(500)
                                .bodyValue("Internal server error: " + error.getMessage()));
    }

    public Mono<ServerResponse> validate(ServerRequest serverRequest) {
        String username = serverRequest.pathVariable("username");
        String languageParam = serverRequest.queryParam("language").orElse(Language.EN.getCode());

        return Mono.fromCallable(() -> parseLanguage(languageParam))
                .flatMap(language -> createValidationRequest(username, language))
                .flatMap(usernameValidationUseCase::validate)
                .map(validationResponseMapper::toDto)
                .flatMap(dto -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(dto))
                .onErrorResume(IllegalArgumentException.class, error ->
                        ServerResponse.badRequest()
                                .bodyValue(Map.of(
                                        "error", "INVALID_REQUEST",
                                        "message", error.getMessage(),
                                        "timestamp", Instant.now()
                                )))
                .onErrorResume(Exception.class, error ->
                        ServerResponse.status(500)
                                .bodyValue(Map.of(
                                        "error", "INTERNAL_SERVER_ERROR",
                                        "message", "Validation service error: " + error.getMessage(),
                                        "timestamp", Instant.now()
                                )));
    }

    public Mono<ServerResponse> markUsed(ServerRequest serverRequest) {
        String username = serverRequest.pathVariable("username");

        return usernameMarkUsedUseCase.markAsUsed(username)
                .map(markUsedResponseMapper::toDto)
                .flatMap(dto -> {
                    if (dto.marked()) {
                        return ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(dto);
                    } else {
                        // Already used - return 200 OK with wasAlreadyUsed=true (idempotent)
                        return ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(dto);
                    }
                }).onErrorResume(IllegalArgumentException.class, error ->
                        ServerResponse.badRequest()
                                .bodyValue(Map.of(
                                        "error", "INVALID_REQUEST",
                                        "message", error.getMessage(),
                                        "timestamp", Instant.now()
                                )))
                .onErrorResume(Exception.class, error ->
                        ServerResponse.status(500)
                                .bodyValue(Map.of(
                                        "error", "INTERNAL_SERVER_ERROR",
                                        "message", "Failed to mark username as used: " + error.getMessage(),
                                        "timestamp", Instant.now()))
                );
    }

    private Mono<GenerationRequestDto> validateRequest(GenerationRequestDto dto) {
        Set<ConstraintViolation<GenerationRequestDto>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            String errorMessage = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(", "));
            return Mono.error(new IllegalArgumentException("Validation failed: " + errorMessage));
        }
        return Mono.just(dto);
    }

    private Language parseLanguage(String languageParam) {
        try {
            return Language.valueOf(languageParam.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid language: " + languageParam + ". Must be one of: EN, ES"
            );
        }
    }

    private Mono<ValidationRequest> createValidationRequest(String username, Language language) {
        try {
            return Mono.just(ValidationRequest.of(username, language));
        } catch (IllegalArgumentException e) {
            return Mono.error(e);
        }
    }

}
