package com.forge.adapters.inbound.rest;

import com.forge.adapters.inbound.dto.GenerationRequestDto;
import com.forge.adapters.inbound.dto.UsernameResponseDto;
import com.forge.adapters.inbound.dto.ValidationResponseDto;
import com.forge.adapters.inbound.mapper.GenerationRequestMapper;
import com.forge.adapters.inbound.mapper.GenerationResponseMapper;
import com.forge.adapters.inbound.mapper.ValidationResponseMapper;
import com.forge.domain.model.GenerationRequest;
import com.forge.domain.model.GenerationResponse;
import com.forge.domain.model.Language;
import com.forge.domain.model.Username;
import com.forge.domain.model.ValidationRequest;
import com.forge.domain.model.ValidationResult;
import com.forge.domain.ports.inbound.UsernameGenerationUseCase;
import com.forge.domain.ports.inbound.UsernameValidationUseCase;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UsernameHandler Unit Tests")
class UsernameHandlerTest {

    @Mock
    private UsernameGenerationUseCase usernameGenerationUseCase;

    @Mock
    private UsernameValidationUseCase usernameValidationUseCase;

    @Mock
    private GenerationRequestMapper requestMapper;

    @Mock
    private GenerationResponseMapper responseMapper;

    @Mock
    private ValidationResponseMapper validationResponseMapper;

    @Mock
    private Validator validator;

    @Mock
    private ServerRequest serverRequest;

    @Mock
    private ConstraintViolation<GenerationRequestDto> constraintViolation;

    @Mock
    private Path propertyPath;

    private UsernameHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UsernameHandler(
                usernameGenerationUseCase,
                usernameValidationUseCase,
                requestMapper,
                responseMapper,
                validationResponseMapper,
                validator
        );
    }

    @Nested
    @DisplayName("Validate Endpoint Tests")
    class ValidateEndpointTests {

        @Test
        @DisplayName("Should validate username successfully with default language EN")
        void shouldValidateUsernameSuccessfullyWithDefaultLanguageEN() {
            // Given
            String username = "testuser";
            ValidationResult domainResult = ValidationResult.valid(username, Instant.now());
            ValidationResponseDto expectedDto = new ValidationResponseDto(
                    username, true, true, true, true,
                    Collections.emptyList(), 0.98, Instant.now()
            );

            when(serverRequest.pathVariable("username")).thenReturn(username);
            when(serverRequest.queryParam("language")).thenReturn(Optional.empty());
            when(usernameValidationUseCase.validate(any(ValidationRequest.class)))
                    .thenReturn(Mono.just(domainResult));
            when(validationResponseMapper.toDto(domainResult)).thenReturn(expectedDto);

            // When
            Mono<ServerResponse> result = handler.validate(serverRequest);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.statusCode());
                        assertEquals(MediaType.APPLICATION_JSON, response.headers().getContentType());
                    })
                    .verifyComplete();

            verify(usernameValidationUseCase).validate(argThat(req ->
                    req.username().equals(username) &&
                            req.language() == Language.EN
            ));
            verify(validationResponseMapper).toDto(domainResult);
        }

        @Test
        @DisplayName("Should validate username with explicit language EN")
        void shouldValidateUsernameWithExplicitLanguageEN() {
            // Given
            String username = "testuser";
            ValidationResult domainResult = ValidationResult.valid(username, Instant.now());
            ValidationResponseDto expectedDto = new ValidationResponseDto(
                    username, true, true, true, true,
                    Collections.emptyList(), 0.98, Instant.now()
            );

            when(serverRequest.pathVariable("username")).thenReturn(username);
            when(serverRequest.queryParam("language")).thenReturn(Optional.of("en"));
            when(usernameValidationUseCase.validate(any(ValidationRequest.class)))
                    .thenReturn(Mono.just(domainResult));
            when(validationResponseMapper.toDto(domainResult)).thenReturn(expectedDto);

            // When
            Mono<ServerResponse> result = handler.validate(serverRequest);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.statusCode());
                    })
                    .verifyComplete();

            verify(usernameValidationUseCase).validate(argThat(req ->
                    req.language() == Language.EN
            ));
        }

        @Test
        @DisplayName("Should validate username with explicit language ES")
        void shouldValidateUsernameWithExplicitLanguageES() {
            // Given
            String username = "usuario";
            ValidationResult domainResult = ValidationResult.valid(username, Instant.now());
            ValidationResponseDto expectedDto = new ValidationResponseDto(
                    username, true, true, true, true,
                    Collections.emptyList(), 0.98, Instant.now()
            );

            when(serverRequest.pathVariable("username")).thenReturn(username);
            when(serverRequest.queryParam("language")).thenReturn(Optional.of("ES"));
            when(usernameValidationUseCase.validate(any(ValidationRequest.class)))
                    .thenReturn(Mono.just(domainResult));
            when(validationResponseMapper.toDto(domainResult)).thenReturn(expectedDto);

            // When
            Mono<ServerResponse> result = handler.validate(serverRequest);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.statusCode());
                    })
                    .verifyComplete();

            verify(usernameValidationUseCase).validate(argThat(req ->
                    req.username().equals(username) &&
                            req.language() == Language.ES
            ));
        }

        @Test
        @DisplayName("Should return bad request when language is invalid")
        void shouldReturnBadRequestWhenLanguageIsInvalid() {
            // Given
            when(serverRequest.pathVariable("username")).thenReturn("testuser");
            when(serverRequest.queryParam("language")).thenReturn(Optional.of("FR"));

            // When
            Mono<ServerResponse> result = handler.validate(serverRequest);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode());
                    })
                    .verifyComplete();

            verifyNoInteractions(usernameValidationUseCase);
        }

        @Test
        @DisplayName("Should return bad request with error details when language is invalid")
        void shouldReturnBadRequestWithErrorDetailsWhenLanguageIsInvalid() {
            // Given
            when(serverRequest.pathVariable("username")).thenReturn("testuser");
            when(serverRequest.queryParam("language")).thenReturn(Optional.of("INVALID"));

            // When
            Mono<ServerResponse> result = handler.validate(serverRequest);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return bad request when username is blank")
        void shouldReturnBadRequestWhenUsernameIsBlank() {
            // Given
            String invalidUsername = "   "; // blank (only whitespace)
            when(serverRequest.pathVariable("username")).thenReturn(invalidUsername);
            when(serverRequest.queryParam("language")).thenReturn(Optional.empty());

            // When
            Mono<ServerResponse> result = handler.validate(serverRequest);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode());
                    })
                    .verifyComplete();

            verifyNoInteractions(usernameValidationUseCase);
        }

        @Test
        @DisplayName("Should return internal server error when use case fails")
        void shouldReturnInternalServerErrorWhenUseCaseFails() {
            // Given
            when(serverRequest.pathVariable("username")).thenReturn("testuser");
            when(serverRequest.queryParam("language")).thenReturn(Optional.empty());
            when(usernameValidationUseCase.validate(any()))
                    .thenReturn(Mono.error(new RuntimeException("Database error")));

            // When
            Mono<ServerResponse> result = handler.validate(serverRequest);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode());
                    })
                    .verifyComplete();

            verify(usernameValidationUseCase).validate(any());
        }

        @Test
        @DisplayName("Should handle validation for username at minimum length boundary")
        void shouldHandleValidationForUsernameAtMinimumLengthBoundary() {
            // Given
            String username = "abcde"; // minimum 5 chars
            ValidationResult domainResult = ValidationResult.valid(username, Instant.now());
            ValidationResponseDto expectedDto = new ValidationResponseDto(
                    username, true, true, true, true,
                    Collections.emptyList(), 0.98, Instant.now()
            );

            when(serverRequest.pathVariable("username")).thenReturn(username);
            when(serverRequest.queryParam("language")).thenReturn(Optional.empty());
            when(usernameValidationUseCase.validate(any(ValidationRequest.class)))
                    .thenReturn(Mono.just(domainResult));
            when(validationResponseMapper.toDto(domainResult)).thenReturn(expectedDto);

            // When
            Mono<ServerResponse> result = handler.validate(serverRequest);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.statusCode());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle validation for username with special characters")
        void shouldHandleValidationForUsernameWithSpecialCharacters() {
            // Given
            String username = "user_name-123";
            ValidationResult domainResult = ValidationResult.valid(username, Instant.now());
            ValidationResponseDto expectedDto = new ValidationResponseDto(
                    username, true, true, true, true,
                    Collections.emptyList(), 0.98, Instant.now()
            );

            when(serverRequest.pathVariable("username")).thenReturn(username);
            when(serverRequest.queryParam("language")).thenReturn(Optional.empty());
            when(usernameValidationUseCase.validate(any(ValidationRequest.class)))
                    .thenReturn(Mono.just(domainResult));
            when(validationResponseMapper.toDto(domainResult)).thenReturn(expectedDto);

            // When
            Mono<ServerResponse> result = handler.validate(serverRequest);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.statusCode());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should map validation result to DTO correctly")
        void shouldMapValidationResultToDtoCorrectly() {
            // Given
            String username = "testuser";
            ValidationResult domainResult = ValidationResult.valid(username, Instant.now());
            ValidationResponseDto expectedDto = new ValidationResponseDto(
                    username, true, true, true, true,
                    Collections.emptyList(), 0.98, Instant.now()
            );

            when(serverRequest.pathVariable("username")).thenReturn(username);
            when(serverRequest.queryParam("language")).thenReturn(Optional.empty());
            when(usernameValidationUseCase.validate(any(ValidationRequest.class)))
                    .thenReturn(Mono.just(domainResult));
            when(validationResponseMapper.toDto(domainResult)).thenReturn(expectedDto);

            // When
            Mono<ServerResponse> result = handler.validate(serverRequest);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.statusCode());
                    })
                    .verifyComplete();

            verify(validationResponseMapper).toDto(domainResult);
        }
    }

    @Nested
    @DisplayName("Generate Endpoint Tests")
    class GenerateEndpointTests {

        @Test
        @DisplayName("Should generate usernames successfully with valid request")
        void shouldGenerateUsernamesSuccessfullyWithValidRequest() {
            // Given
            GenerationRequestDto requestDto = new GenerationRequestDto("EN", 3);
            GenerationRequest domainRequest = new GenerationRequest(Language.EN, 3);
            GenerationResponse domainResponse = new GenerationResponse(
                    List.of(
                            Username.of("user_1", Language.EN),
                            Username.of("user_2", Language.EN),
                            Username.of("user_3", Language.EN)
                    ),
                    Instant.now(),
                    Language.EN,
                    3,
                    false,
                    100L
            );
            UsernameResponseDto responseDto = new UsernameResponseDto(
                    List.of("user1", "user2", "user3"),
                    Instant.now(),
                    "EN",
                    3,
                    false,
                    100L
            );

            when(serverRequest.bodyToMono(GenerationRequestDto.class))
                    .thenReturn(Mono.just(requestDto));
            when(validator.validate(requestDto)).thenReturn(Collections.emptySet());
            when(requestMapper.toDomain(requestDto)).thenReturn(domainRequest);
            when(usernameGenerationUseCase.generate(domainRequest))
                    .thenReturn(Mono.just(domainResponse));
            when(responseMapper.toDto(domainResponse)).thenReturn(responseDto);

            // When
            Mono<ServerResponse> result = handler.generate(serverRequest);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.statusCode());
                        assertEquals(MediaType.APPLICATION_JSON, response.headers().getContentType());
                    })
                    .verifyComplete();

            verify(validator).validate(requestDto);
            verify(requestMapper).toDomain(requestDto);
            verify(usernameGenerationUseCase).generate(domainRequest);
            verify(responseMapper).toDto(domainResponse);
        }

        @Test
        @DisplayName("Should return bad request when DTO validation fails")
        void shouldReturnBadRequestWhenDtoValidationFails() {
            // Given
            GenerationRequestDto requestDto = new GenerationRequestDto("INVALID", -1);
            Set<ConstraintViolation<GenerationRequestDto>> violations = new HashSet<>();
            violations.add(constraintViolation);

            when(serverRequest.bodyToMono(GenerationRequestDto.class))
                    .thenReturn(Mono.just(requestDto));
            when(validator.validate(requestDto)).thenReturn(violations);
            when(constraintViolation.getMessage()).thenReturn("Invalid count");

            // When
            Mono<ServerResponse> result = handler.generate(serverRequest);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode());
                    })
                    .verifyComplete();

            verify(validator).validate(requestDto);
            verifyNoInteractions(requestMapper);
            verifyNoInteractions(usernameGenerationUseCase);
        }

        @Test
        @DisplayName("Should return bad request with multiple constraint violations")
        void shouldReturnBadRequestWithMultipleConstraintViolations() {
            // Given
            GenerationRequestDto requestDto = new GenerationRequestDto(null, 0);
            Set<ConstraintViolation<GenerationRequestDto>> violations = new HashSet<>();

            ConstraintViolation<GenerationRequestDto> violation1 = mock(ConstraintViolation.class);
            ConstraintViolation<GenerationRequestDto> violation2 = mock(ConstraintViolation.class);

            when(violation1.getMessage()).thenReturn("Language cannot be null");
            when(violation2.getMessage()).thenReturn("Count must be positive");

            violations.add(violation1);
            violations.add(violation2);

            when(serverRequest.bodyToMono(GenerationRequestDto.class))
                    .thenReturn(Mono.just(requestDto));
            when(validator.validate(requestDto)).thenReturn(violations);

            // When
            Mono<ServerResponse> result = handler.generate(serverRequest);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return bad request when mapper throws IllegalArgumentException")
        void shouldReturnBadRequestWhenMapperThrowsIllegalArgumentException() {
            // Given
            GenerationRequestDto requestDto = new GenerationRequestDto("EN", 3);

            when(serverRequest.bodyToMono(GenerationRequestDto.class))
                    .thenReturn(Mono.just(requestDto));
            when(validator.validate(requestDto)).thenReturn(Collections.emptySet());
            when(requestMapper.toDomain(requestDto))
                    .thenThrow(new IllegalArgumentException("Invalid language"));

            // When
            Mono<ServerResponse> result = handler.generate(serverRequest);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode());
                    })
                    .verifyComplete();

            verifyNoInteractions(usernameGenerationUseCase);
        }

        @Test
        @DisplayName("Should return internal server error when use case fails")
        void shouldReturnInternalServerErrorWhenUseCaseFails() {
            // Given
            GenerationRequestDto requestDto = new GenerationRequestDto("EN", 3);
            GenerationRequest domainRequest = new GenerationRequest(Language.EN, 3);

            when(serverRequest.bodyToMono(GenerationRequestDto.class))
                    .thenReturn(Mono.just(requestDto));
            when(validator.validate(requestDto)).thenReturn(Collections.emptySet());
            when(requestMapper.toDomain(requestDto)).thenReturn(domainRequest);
            when(usernameGenerationUseCase.generate(domainRequest))
                    .thenReturn(Mono.error(new RuntimeException("Service unavailable")));

            // When
            Mono<ServerResponse> result = handler.generate(serverRequest);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode());
                    })
                    .verifyComplete();

            verify(usernameGenerationUseCase).generate(domainRequest);
        }

        @Test
        @DisplayName("Should handle generation with minimum count")
        void shouldHandleGenerationWithMinimumCount() {
            // Given
            GenerationRequestDto requestDto = new GenerationRequestDto("EN", 1);
            GenerationRequest domainRequest = new GenerationRequest(Language.EN, 1);
            GenerationResponse domainResponse = new GenerationResponse(
                    List.of(Username.of("user_1", Language.EN)),
                    Instant.now(),
                    Language.EN,
                    1,
                    false,
                    100L
            );
            UsernameResponseDto responseDto = new UsernameResponseDto(
                    List.of("user1"),
                    Instant.now(),
                    "EN",
                    1,
                    false,
                    100L
            );

            when(serverRequest.bodyToMono(GenerationRequestDto.class))
                    .thenReturn(Mono.just(requestDto));
            when(validator.validate(requestDto)).thenReturn(Collections.emptySet());
            when(requestMapper.toDomain(requestDto)).thenReturn(domainRequest);
            when(usernameGenerationUseCase.generate(domainRequest))
                    .thenReturn(Mono.just(domainResponse));
            when(responseMapper.toDto(domainResponse)).thenReturn(responseDto);

            // When
            Mono<ServerResponse> result = handler.generate(serverRequest);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.statusCode());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle generation with maximum count")
        void shouldHandleGenerationWithMaximumCount() {
            // Given
            GenerationRequestDto requestDto = new GenerationRequestDto("ES", 10);
            GenerationRequest domainRequest = new GenerationRequest(Language.ES, 10);
            List<Username> usernames = new ArrayList<>();
            List<String> usernameStrings = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                usernames.add(Username.of("user_" + i, Language.ES));
                usernameStrings.add("user" + i);
            }
            GenerationResponse domainResponse = new GenerationResponse(
                    usernames,
                    Instant.now(),
                    Language.ES,
                    10,
                    false,
                    100L
            );
            UsernameResponseDto responseDto = new UsernameResponseDto(
                    usernameStrings,
                    Instant.now(),
                    "ES",
                    10,
                    false,
                    100L
            );

            when(serverRequest.bodyToMono(GenerationRequestDto.class))
                    .thenReturn(Mono.just(requestDto));
            when(validator.validate(requestDto)).thenReturn(Collections.emptySet());
            when(requestMapper.toDomain(requestDto)).thenReturn(domainRequest);
            when(usernameGenerationUseCase.generate(domainRequest))
                    .thenReturn(Mono.just(domainResponse));
            when(responseMapper.toDto(domainResponse)).thenReturn(responseDto);

            // When
            Mono<ServerResponse> result = handler.generate(serverRequest);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.statusCode());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle generation with Spanish language")
        void shouldHandleGenerationWithSpanishLanguage() {
            // Given
            GenerationRequestDto requestDto = new GenerationRequestDto("ES", 3);
            GenerationRequest domainRequest = new GenerationRequest(Language.ES, 3);
            GenerationResponse domainResponse = new GenerationResponse(
                    List.of(
                            Username.of("usuario_1", Language.ES),
                            Username.of("usuario_2", Language.ES),
                            Username.of("usuario_3", Language.ES)
                    ),
                    Instant.now(),
                    Language.ES,
                    3,
                    false,
                    100L
            );
            UsernameResponseDto responseDto = new UsernameResponseDto(
                    List.of("usuario1", "usuario2", "usuario3"),
                    Instant.now(),
                    "ES",
                    3,
                    false,
                    100L
            );

            when(serverRequest.bodyToMono(GenerationRequestDto.class))
                    .thenReturn(Mono.just(requestDto));
            when(validator.validate(requestDto)).thenReturn(Collections.emptySet());
            when(requestMapper.toDomain(requestDto)).thenReturn(domainRequest);
            when(usernameGenerationUseCase.generate(domainRequest))
                    .thenReturn(Mono.just(domainResponse));
            when(responseMapper.toDto(domainResponse)).thenReturn(responseDto);

            // When
            Mono<ServerResponse> result = handler.generate(serverRequest);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.statusCode());
                    })
                    .verifyComplete();

            verify(requestMapper).toDomain(argThat(dto ->
                    dto.language().equals("ES")
            ));
        }
    }

    @Nested
    @DisplayName("Language Parsing Tests")
    class LanguageParsingTests {

        @Test
        @DisplayName("Should parse uppercase EN correctly")
        void shouldParseUppercaseENCorrectly() {
            // Given
            String username = "testuser";
            ValidationResult domainResult = ValidationResult.valid(username, Instant.now());
            ValidationResponseDto expectedDto = new ValidationResponseDto(
                    username, true, true, true, true,
                    Collections.emptyList(), 0.98, Instant.now()
            );

            when(serverRequest.pathVariable("username")).thenReturn(username);
            when(serverRequest.queryParam("language")).thenReturn(Optional.of("EN"));
            when(usernameValidationUseCase.validate(any(ValidationRequest.class)))
                    .thenReturn(Mono.just(domainResult));
            when(validationResponseMapper.toDto(domainResult)).thenReturn(expectedDto);

            // When
            Mono<ServerResponse> result = handler.validate(serverRequest);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.statusCode());
                    })
                    .verifyComplete();

            verify(usernameValidationUseCase).validate(argThat(req ->
                    req.language() == Language.EN
            ));
        }

        @Test
        @DisplayName("Should parse lowercase en correctly")
        void shouldParseLowercaseEnCorrectly() {
            // Given
            String username = "testuser";
            ValidationResult domainResult = ValidationResult.valid(username, Instant.now());
            ValidationResponseDto expectedDto = new ValidationResponseDto(
                    username, true, true, true, true,
                    Collections.emptyList(), 0.98, Instant.now()
            );

            when(serverRequest.pathVariable("username")).thenReturn(username);
            when(serverRequest.queryParam("language")).thenReturn(Optional.of("en"));
            when(usernameValidationUseCase.validate(any(ValidationRequest.class)))
                    .thenReturn(Mono.just(domainResult));
            when(validationResponseMapper.toDto(domainResult)).thenReturn(expectedDto);

            // When
            Mono<ServerResponse> result = handler.validate(serverRequest);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.statusCode());
                    })
                    .verifyComplete();

            verify(usernameValidationUseCase).validate(argThat(req ->
                    req.language() == Language.EN
            ));
        }

        @Test
        @DisplayName("Should parse mixed case Es correctly")
        void shouldParseMixedCaseEsCorrectly() {
            // Given
            String username = "testuser";
            ValidationResult domainResult = ValidationResult.valid(username, Instant.now());
            ValidationResponseDto expectedDto = new ValidationResponseDto(
                    username, true, true, true, true,
                    Collections.emptyList(), 0.98, Instant.now()
            );

            when(serverRequest.pathVariable("username")).thenReturn(username);
            when(serverRequest.queryParam("language")).thenReturn(Optional.of("Es"));
            when(usernameValidationUseCase.validate(any(ValidationRequest.class)))
                    .thenReturn(Mono.just(domainResult));
            when(validationResponseMapper.toDto(domainResult)).thenReturn(expectedDto);

            // When
            Mono<ServerResponse> result = handler.validate(serverRequest);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.OK, response.statusCode());
                    })
                    .verifyComplete();

            verify(usernameValidationUseCase).validate(argThat(req ->
                    req.language() == Language.ES
            ));
        }

        @Test
        @DisplayName("Should throw exception for unsupported language FR")
        void shouldThrowExceptionForUnsupportedLanguageFR() {
            // Given
            when(serverRequest.pathVariable("username")).thenReturn("testuser");
            when(serverRequest.queryParam("language")).thenReturn(Optional.of("FR"));

            // When
            Mono<ServerResponse> result = handler.validate(serverRequest);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should throw exception for empty language string")
        void shouldThrowExceptionForEmptyLanguageString() {
            // Given
            when(serverRequest.pathVariable("username")).thenReturn("testuser");
            when(serverRequest.queryParam("language")).thenReturn(Optional.of(""));

            // When
            Mono<ServerResponse> result = handler.validate(serverRequest);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode());
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Reactive Flow Tests")
    class ReactiveFlowTests {

        @Test
        @DisplayName("Should complete validate flow reactively without blocking")
        void shouldCompleteValidateFlowReactivelyWithoutBlocking() {
            // Given
            String username = "testuser";
            ValidationResult domainResult = ValidationResult.valid(username, Instant.now());
            ValidationResponseDto expectedDto = new ValidationResponseDto(
                    username, true, true, true, true,
                    Collections.emptyList(), 0.98, Instant.now()
            );

            when(serverRequest.pathVariable("username")).thenReturn(username);
            when(serverRequest.queryParam("language")).thenReturn(Optional.empty());
            when(usernameValidationUseCase.validate(any(ValidationRequest.class)))
                    .thenReturn(Mono.just(domainResult));
            when(validationResponseMapper.toDto(domainResult)).thenReturn(expectedDto);

            // When
            Mono<ServerResponse> result = handler.validate(serverRequest);

            // Then - verify the Mono completes without blocking
            StepVerifier.create(result)
                    .expectNextCount(1)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should complete generate flow reactively without blocking")
        void shouldCompleteGenerateFlowReactivelyWithoutBlocking() {
            // Given
            GenerationRequestDto requestDto = new GenerationRequestDto("EN", 3);
            GenerationRequest domainRequest = new GenerationRequest(Language.EN, 3);
            GenerationResponse domainResponse = new GenerationResponse(
                    List.of(
                            Username.of("user_1", Language.EN),
                            Username.of("user_2", Language.EN),
                            Username.of("user_3", Language.EN)
                    ),
                    Instant.now(),
                    Language.EN,
                    3,
                    false,
                    100L
            );
            UsernameResponseDto responseDto = new UsernameResponseDto(
                    List.of("user1", "user2", "user3"),
                    Instant.now(),
                    "EN",
                    3,
                    false,
                    100L
            );

            when(serverRequest.bodyToMono(GenerationRequestDto.class))
                    .thenReturn(Mono.just(requestDto));
            when(validator.validate(requestDto)).thenReturn(Collections.emptySet());
            when(requestMapper.toDomain(requestDto)).thenReturn(domainRequest);
            when(usernameGenerationUseCase.generate(domainRequest))
                    .thenReturn(Mono.just(domainResponse));
            when(responseMapper.toDto(domainResponse)).thenReturn(responseDto);

            // When
            Mono<ServerResponse> result = handler.generate(serverRequest);

            // Then - verify the Mono completes without blocking
            StepVerifier.create(result)
                    .expectNextCount(1)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle error in reactive chain for validate endpoint")
        void shouldHandleErrorInReactiveChainForValidateEndpoint() {
            // Given
            when(serverRequest.pathVariable("username")).thenReturn("testuser");
            when(serverRequest.queryParam("language")).thenReturn(Optional.empty());
            when(usernameValidationUseCase.validate(any()))
                    .thenReturn(Mono.error(new RuntimeException("Service error")));

            // When
            Mono<ServerResponse> result = handler.validate(serverRequest);

            // Then - error should be handled and converted to error response
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle error in reactive chain for generate endpoint")
        void shouldHandleErrorInReactiveChainForGenerateEndpoint() {
            // Given
            GenerationRequestDto requestDto = new GenerationRequestDto("EN", 3);
            GenerationRequest domainRequest = new GenerationRequest(Language.EN, 3);

            when(serverRequest.bodyToMono(GenerationRequestDto.class))
                    .thenReturn(Mono.just(requestDto));
            when(validator.validate(requestDto)).thenReturn(Collections.emptySet());
            when(requestMapper.toDomain(requestDto)).thenReturn(domainRequest);
            when(usernameGenerationUseCase.generate(domainRequest))
                    .thenReturn(Mono.error(new RuntimeException("Generation error")));

            // When
            Mono<ServerResponse> result = handler.generate(serverRequest);

            // Then - error should be handled and converted to error response
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode());
                    })
                    .verifyComplete();
        }
    }
}