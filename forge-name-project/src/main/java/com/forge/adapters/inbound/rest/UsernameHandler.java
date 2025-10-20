package com.forge.adapters.inbound.rest;

import com.forge.adapters.inbound.dto.GenerationRequestDto;
import com.forge.adapters.inbound.mapper.GenerationRequestMapper;
import com.forge.adapters.inbound.mapper.GenerationResponseMapper;
import com.forge.domain.ports.inbound.UsernameGenerationUseCase;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UsernameHandler {

    private final UsernameGenerationUseCase usernameGenerationUseCase;
    private final GenerationRequestMapper requestMapper;
    private final GenerationResponseMapper responseMapper;
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

}
