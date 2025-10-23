package com.forge.adapters.inbound.graphql.resolver;

import com.forge.adapters.inbound.graphql.mapper.GraphQLValidationMapper;
import com.forge.adapters.inbound.graphql.type.ValidationResponse;
import com.forge.domain.model.Language;
import com.forge.domain.model.ValidationRequest;
import com.forge.domain.model.ValidationResult;
import com.forge.domain.ports.inbound.UsernameValidationUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Username Query Resolver Tests")
class UsernameQueryResolverTest {

    @Mock
    private UsernameValidationUseCase usernameValidationUseCase;

    @Mock
    private GraphQLValidationMapper validationMapper;

    @InjectMocks
    private UsernameQueryResolver resolver;

    private Instant testInstant;

    @BeforeEach
    void setUp() {
        testInstant = Instant.parse("2025-01-15T10:30:00Z");
    }

    @Test
    @DisplayName("Should return true for health check query")
    void shouldReturnTrueForHealthCheckQuery() {
        // When
        Boolean health = resolver.health();

        // Then
        assertNotNull(health);
        assertTrue(health);
    }

    @Test
    @DisplayName("Should validate username with English language successfully")
    void shouldValidateUsernameWithEnglishLanguageSuccessfully() {
        // Given
        String username = "validuser123";
        Language language = Language.EN;

        ValidationResult domainResult = ValidationResult.valid(username, testInstant);
        ValidationResponse graphqlResponse = ValidationResponse.of(
                username,
                true,
                true,
                true,
                true,
                Collections.emptyList(),
                0.98,
                testInstant
        );

        when(usernameValidationUseCase.validate(any(ValidationRequest.class)))
                .thenReturn(Mono.just(domainResult));
        when(validationMapper.toGraphQL(domainResult))
                .thenReturn(graphqlResponse);

        // When
        Mono<ValidationResponse> result = resolver.validateUsername(username, language);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(username, response.username());
                    assertTrue(response.isValid());
                    assertTrue(response.isUnique());
                    assertTrue(response.isAppropriate());
                    assertTrue(response.isValidFormat());
                    assertTrue(response.reasons().isEmpty());
                    assertEquals(0.98, response.confidenceScore());
                })
                .verifyComplete();

        verify(usernameValidationUseCase).validate(any(ValidationRequest.class));
        verify(validationMapper).toGraphQL(domainResult);
    }

    @Test
    @DisplayName("Should validate username with Spanish language successfully")
    void shouldValidateUsernameWithSpanishLanguageSuccessfully() {
        // Given
        String username = "usuariovalido";
        Language language = Language.ES;

        ValidationResult domainResult = ValidationResult.valid(username, testInstant);
        ValidationResponse graphqlResponse = ValidationResponse.of(
                username,
                true,
                true,
                true,
                true,
                Collections.emptyList(),
                0.98,
                testInstant
        );

        when(usernameValidationUseCase.validate(any(ValidationRequest.class)))
                .thenReturn(Mono.just(domainResult));
        when(validationMapper.toGraphQL(domainResult))
                .thenReturn(graphqlResponse);

        // When
        Mono<ValidationResponse> result = resolver.validateUsername(username, language);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(username, response.username());
                    assertTrue(response.isValid());
                })
                .verifyComplete();

        verify(usernameValidationUseCase).validate(any(ValidationRequest.class));
        verify(validationMapper).toGraphQL(domainResult);
    }

    @Test
    @DisplayName("Should default to English language when language parameter is null")
    void shouldDefaultToEnglishLanguageWhenLanguageParameterIsNull() {
        // Given
        String username = "testuser";
        Language language = null;

        ValidationResult domainResult = ValidationResult.valid(username, testInstant);
        ValidationResponse graphqlResponse = ValidationResponse.of(
                username,
                true,
                true,
                true,
                true,
                Collections.emptyList(),
                0.98,
                testInstant
        );

        when(usernameValidationUseCase.validate(any(ValidationRequest.class)))
                .thenReturn(Mono.just(domainResult));
        when(validationMapper.toGraphQL(domainResult))
                .thenReturn(graphqlResponse);

        // When
        Mono<ValidationResponse> result = resolver.validateUsername(username, language);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response);
                    assertEquals(username, response.username());
                })
                .verifyComplete();

        // Verify that the use case was called with EN language (default)
        verify(usernameValidationUseCase).validate(argThat(request ->
                request.username().equals(username) && request.language() == Language.EN
        ));
    }

    @Test
    @DisplayName("Should return validation failure for invalid username")
    void shouldReturnValidationFailureForInvalidUsername() {
        // Given
        String username = "bad@user!";
        Language language = Language.EN;
        List<String> reasons = Arrays.asList(
                "Username contains invalid characters",
                "Username format is invalid"
        );

        ValidationResult domainResult = ValidationResult.invalid(
                username,
                true,
                true,
                false,
                reasons,
                testInstant
        );

        ValidationResponse graphqlResponse = ValidationResponse.of(
                username,
                false,
                true,
                true,
                false,
                reasons,
                0.85,
                testInstant
        );

        when(usernameValidationUseCase.validate(any(ValidationRequest.class)))
                .thenReturn(Mono.just(domainResult));
        when(validationMapper.toGraphQL(domainResult))
                .thenReturn(graphqlResponse);

        // When
        Mono<ValidationResponse> result = resolver.validateUsername(username, language);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(username, response.username());
                    assertFalse(response.isValid());
                    assertFalse(response.isValidFormat());
                    assertEquals(2, response.reasons().size());
                    assertTrue(response.reasons().contains("Username contains invalid characters"));
                })
                .verifyComplete();

        verify(usernameValidationUseCase).validate(any(ValidationRequest.class));
        verify(validationMapper).toGraphQL(domainResult);
    }

    @Test
    @DisplayName("Should return validation failure when username is not unique")
    void shouldReturnValidationFailureWhenUsernameIsNotUnique() {
        // Given
        String username = "existinguser";
        Language language = Language.EN;
        List<String> reasons = Arrays.asList("Username already exists");

        ValidationResult domainResult = ValidationResult.invalid(
                username,
                false,
                true,
                true,
                reasons,
                testInstant
        );

        ValidationResponse graphqlResponse = ValidationResponse.of(
                username,
                false,
                false,
                true,
                true,
                reasons,
                0.85,
                testInstant
        );

        when(usernameValidationUseCase.validate(any(ValidationRequest.class)))
                .thenReturn(Mono.just(domainResult));
        when(validationMapper.toGraphQL(domainResult))
                .thenReturn(graphqlResponse);

        // When
        Mono<ValidationResponse> result = resolver.validateUsername(username, language);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertFalse(response.isValid());
                    assertFalse(response.isUnique());
                    assertTrue(response.isAppropriate());
                    assertTrue(response.isValidFormat());
                    assertTrue(response.reasons().contains("Username already exists"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return validation failure when username is inappropriate")
    void shouldReturnValidationFailureWhenUsernameIsInappropriate() {
        // Given
        String username = "badword123";
        Language language = Language.EN;
        List<String> reasons = Arrays.asList("Username contains inappropriate content");

        ValidationResult domainResult = ValidationResult.invalid(
                username,
                true,
                false,
                true,
                reasons,
                testInstant
        );

        ValidationResponse graphqlResponse = ValidationResponse.of(
                username,
                false,
                true,
                false,
                true,
                reasons,
                0.85,
                testInstant
        );

        when(usernameValidationUseCase.validate(any(ValidationRequest.class)))
                .thenReturn(Mono.just(domainResult));
        when(validationMapper.toGraphQL(domainResult))
                .thenReturn(graphqlResponse);

        // When
        Mono<ValidationResponse> result = resolver.validateUsername(username, language);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertFalse(response.isValid());
                    assertTrue(response.isUnique());
                    assertFalse(response.isAppropriate());
                    assertTrue(response.isValidFormat());
                    assertTrue(response.reasons().contains("Username contains inappropriate content"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return validation failure with multiple reasons")
    void shouldReturnValidationFailureWithMultipleReasons() {
        // Given
        String username = "bad";
        Language language = Language.EN;
        List<String> reasons = Arrays.asList(
                "Username too short",
                "Username not unique",
                "Username may be inappropriate"
        );

        ValidationResult domainResult = ValidationResult.invalid(
                username,
                false,
                false,
                false,
                reasons,
                testInstant
        );

        ValidationResponse graphqlResponse = ValidationResponse.of(
                username,
                false,
                false,
                false,
                false,
                reasons,
                0.10,
                testInstant
        );

        when(usernameValidationUseCase.validate(any(ValidationRequest.class)))
                .thenReturn(Mono.just(domainResult));
        when(validationMapper.toGraphQL(domainResult))
                .thenReturn(graphqlResponse);

        // When
        Mono<ValidationResponse> result = resolver.validateUsername(username, language);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertFalse(response.isValid());
                    assertFalse(response.isUnique());
                    assertFalse(response.isAppropriate());
                    assertFalse(response.isValidFormat());
                    assertEquals(3, response.reasons().size());
                    assertEquals(0.10, response.confidenceScore());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle reactive error from validation use case")
    void shouldHandleReactiveErrorFromValidationUseCase() {
        // Given
        String username = "testuser";
        Language language = Language.EN;
        RuntimeException error = new RuntimeException("Validation service unavailable");

        when(usernameValidationUseCase.validate(any(ValidationRequest.class)))
                .thenReturn(Mono.error(error));

        // When
        Mono<ValidationResponse> result = resolver.validateUsername(username, language);

        // Then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                                throwable.getMessage().equals("Validation service unavailable")
                )
                .verify();

        verify(usernameValidationUseCase).validate(any(ValidationRequest.class));
        verify(validationMapper, never()).toGraphQL(any());
    }

    @Test
    @DisplayName("Should create correct ValidationRequest with provided language")
    void shouldCreateCorrectValidationRequestWithProvidedLanguage() {
        // Given
        String username = "testuser";
        Language language = Language.ES;

        ValidationResult domainResult = ValidationResult.valid(username, testInstant);
        ValidationResponse graphqlResponse = ValidationResponse.of(
                username,
                true,
                true,
                true,
                true,
                Collections.emptyList(),
                0.98,
                testInstant
        );

        when(usernameValidationUseCase.validate(any(ValidationRequest.class)))
                .thenReturn(Mono.just(domainResult));
        when(validationMapper.toGraphQL(domainResult))
                .thenReturn(graphqlResponse);

        // When
        Mono<ValidationResponse> result = resolver.validateUsername(username, language);

        // Then
        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();

        verify(usernameValidationUseCase).validate(argThat(request ->
                request.username().equals(username) && request.language() == Language.ES
        ));
    }

    @Test
    @DisplayName("Should map domain result to GraphQL response correctly")
    void shouldMapDomainResultToGraphQLResponseCorrectly() {
        // Given
        String username = "maptest";
        Language language = Language.EN;

        ValidationResult domainResult = ValidationResult.valid(username, testInstant);
        ValidationResponse expectedResponse = ValidationResponse.of(
                username,
                true,
                true,
                true,
                true,
                Collections.emptyList(),
                0.98,
                testInstant
        );

        when(usernameValidationUseCase.validate(any(ValidationRequest.class)))
                .thenReturn(Mono.just(domainResult));
        when(validationMapper.toGraphQL(domainResult))
                .thenReturn(expectedResponse);

        // When
        Mono<ValidationResponse> result = resolver.validateUsername(username, language);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertSame(expectedResponse, response);
                })
                .verifyComplete();

        verify(validationMapper).toGraphQL(domainResult);
    }

    @Test
    @DisplayName("Should handle validation with current timestamp")
    void shouldHandleValidationWithCurrentTimestamp() {
        // Given
        String username = "realtimeuser";
        Language language = Language.EN;
        Instant now = Instant.now();

        ValidationResult domainResult = ValidationResult.valid(username, now);
        ValidationResponse graphqlResponse = ValidationResponse.of(
                username,
                true,
                true,
                true,
                true,
                Collections.emptyList(),
                0.98,
                now
        );

        when(usernameValidationUseCase.validate(any(ValidationRequest.class)))
                .thenReturn(Mono.just(domainResult));
        when(validationMapper.toGraphQL(domainResult))
                .thenReturn(graphqlResponse);

        // When
        Mono<ValidationResponse> result = resolver.validateUsername(username, language);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response.validatedAt());
                    assertEquals(now.toString(), response.validatedAt());
                })
                .verifyComplete();
    }
}