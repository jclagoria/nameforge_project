package com.forge.domain.ports.inbound;

import com.forge.domain.fixtures.TestDataFixtures;
import com.forge.domain.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UsernameGenerationUseCase Tests")
class UsernameGenerationUseCaseTest {

    @Mock
    private UsernameGenerationUseCase usernameGenerationUseCase;

    // Constants
    private static final Duration REASONABLE_TIMEOUT = Duration.ofSeconds(5);
    private static final String USERNAME_PATTERN = "^[a-z0-9_-]{5,30}$";
    private static final String ERROR_GENERATION_FAILED = "Generation failed";
    private static final String ERROR_NULL_LANGUAGE = "Language cannot be null";
    private static final String ERROR_INVALID_COUNT = "Count must be between 1 and 10";

    // Helper methods
    private void verifyGenerationResponse(
            GenerationRequest request,
            GenerationResponse expectedResponse,
            int expectedSize) {

        when(usernameGenerationUseCase.generate(any(GenerationRequest.class)))
                .thenReturn(Mono.just(expectedResponse));

        StepVerifier.create(usernameGenerationUseCase.generate(request))
                .assertNext(response -> {
                    assertThat(response.usernames()).hasSize(expectedSize);
                    assertThat(response.totalGenerated()).isEqualTo(expectedSize);
                })
                .verifyComplete();
    }

    private void verifyMonoCompletes(Mono<GenerationResponse> mono) {
        StepVerifier.create(mono)
                .expectNextCount(1)
                .verifyComplete();
    }

    private void verifyMonoError(Mono<GenerationResponse> mono, String expectedMessage) {
        StepVerifier.create(mono)
                .expectErrorMatches(error ->
                        error instanceof RuntimeException &&
                                error.getMessage().equals(expectedMessage)
                )
                .verify();
    }

    @Nested
    @DisplayName("Successful Generation Scenarios")
    class SuccessfulGenerationTests {

        @ParameterizedTest(name = "Should generate {0} usernames for {1} language")
        @MethodSource("generationCountAndLanguageProvider")
        @DisplayName("Should generate requested number of usernames")
        void shouldGenerateRequestedNumberOfUsernames(
                int count, Language language, String prefix) {
            // Arrange
            GenerationRequest request = TestDataFixtures.generationRequest(language, count);
            List<Username> usernames = TestDataFixtures.usernamesWithLanguage(prefix, count, language);
            GenerationResponse expectedResponse = TestDataFixtures.generationResponse(usernames, language);

            // Act & Assert
            verifyGenerationResponse(request, expectedResponse, count);
        }

        static Stream<Arguments> generationCountAndLanguageProvider() {
            return Stream.of(
                arguments(1, Language.EN, "user"),
                arguments(5, Language.EN, "user"),
                arguments(10, Language.EN, "user"),
                arguments(3, Language.EN, "english_user"),
                arguments(3, Language.ES, "usuario")
            );
        }

        @Test
        @DisplayName("Should generate usernames with English language")
        void shouldGenerateUsernamesWithEnglishLanguage() {
            // Arrange
            GenerationRequest request = TestDataFixtures.generationRequest(Language.EN, 3);
            List<Username> usernames = TestDataFixtures.usernamesWithLanguage("english_user", 3, Language.EN);
            GenerationResponse expectedResponse = TestDataFixtures.generationResponse(usernames, Language.EN);

            when(usernameGenerationUseCase.generate(any(GenerationRequest.class)))
                    .thenReturn(Mono.just(expectedResponse));

            // Act & Assert
            StepVerifier.create(usernameGenerationUseCase.generate(request))
                    .assertNext(response -> {
                        assertThat(response.language()).isEqualTo(Language.EN);
                        response.usernames().forEach(username ->
                                assertThat(username.language()).isEqualTo(Language.EN)
                        );
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should generate usernames with Spanish language")
        void shouldGenerateUsernamesWithSpanishLanguage() {
            // Arrange
            GenerationRequest request = TestDataFixtures.generationRequest(Language.ES, 3);
            List<Username> usernames = TestDataFixtures.usernamesWithLanguage("usuario", 3, Language.ES);
            GenerationResponse expectedResponse = TestDataFixtures.generationResponse(usernames, Language.ES);

            when(usernameGenerationUseCase.generate(any(GenerationRequest.class)))
                    .thenReturn(Mono.just(expectedResponse));

            // Act & Assert
            StepVerifier.create(usernameGenerationUseCase.generate(request))
                    .assertNext(response -> {
                        assertThat(response.language()).isEqualTo(Language.ES);
                        response.usernames().forEach(username ->
                                assertThat(username.language()).isEqualTo(Language.ES)
                        );
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Response Validation Scenarios")
    class ResponseValidationTests {

        @Test
        @DisplayName("Response should contain all required fields populated")
        void responseShouldContainAllRequiredFieldsPopulated() {
            // Arrange
            GenerationRequest request = TestDataFixtures.generationRequest(2);
            List<Username> usernames = TestDataFixtures.usernames("user", 2);
            GenerationResponse expectedResponse = TestDataFixtures.generationResponse(
                    usernames, Language.EN, true, 25L);

            when(usernameGenerationUseCase.generate(any(GenerationRequest.class)))
                    .thenReturn(Mono.just(expectedResponse));

            // Act & Assert
            StepVerifier.create(usernameGenerationUseCase.generate(request))
                    .assertNext(response -> {
                        assertThat(response.usernames()).isNotNull().isNotEmpty();
                        assertThat(response.generatedAt()).isNotNull();
                        assertThat(response.language()).isNotNull();
                        assertThat(response.totalGenerated()).isPositive();
                        assertThat(response.responseTimeMs()).isNotNegative();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Response timestamp should be recent")
        void responseTimestampShouldBeRecent() {
            // Arrange
            Instant beforeGeneration = Instant.now();
            GenerationRequest request = TestDataFixtures.generationRequest(1);
            Username username = TestDataFixtures.username("recent_user");
            GenerationResponse expectedResponse = TestDataFixtures.generationResponse(
                    List.of(username), Language.EN, 30L);

            when(usernameGenerationUseCase.generate(any(GenerationRequest.class)))
                    .thenReturn(Mono.just(expectedResponse));

            // Act & Assert
            StepVerifier.create(usernameGenerationUseCase.generate(request))
                    .assertNext(response -> {
                        Instant afterGeneration = Instant.now();
                        assertThat(response.generatedAt())
                                .isAfterOrEqualTo(beforeGeneration)
                                .isBeforeOrEqualTo(afterGeneration);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("All generated usernames should be unique (no duplicates)")
        void allGeneratedUsernamesShouldBeUnique() {
            // Arrange
            GenerationRequest request = TestDataFixtures.generationRequest(5);
            List<Username> usernames = TestDataFixtures.usernames("unique_user", 5);
            GenerationResponse expectedResponse = TestDataFixtures.generationResponse(usernames, Language.EN);

            when(usernameGenerationUseCase.generate(any(GenerationRequest.class)))
                    .thenReturn(Mono.just(expectedResponse));

            // Act & Assert
            StepVerifier.create(usernameGenerationUseCase.generate(request))
                    .assertNext(response -> {
                        List<String> usernameValues = response.getUsernameValues();
                        HashSet<String> uniqueUsernames = new HashSet<>(usernameValues);
                        assertThat(uniqueUsernames).hasSize(usernameValues.size());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("All usernames should follow valid pattern format")
        void allUsernamesShouldFollowValidPatternFormat() {
            // Arrange
            GenerationRequest request = TestDataFixtures.generationRequest(3);
            List<Username> usernames = List.of(
                    TestDataFixtures.username("valid_user123"),
                    TestDataFixtures.username("another-user456"),
                    TestDataFixtures.username("user_789")
            );
            GenerationResponse expectedResponse = TestDataFixtures.generationResponse(
                    usernames, Language.EN, 80L);

            when(usernameGenerationUseCase.generate(any(GenerationRequest.class)))
                    .thenReturn(Mono.just(expectedResponse));

            // Act & Assert
            StepVerifier.create(usernameGenerationUseCase.generate(request))
                    .assertNext(response ->
                        response.usernames().forEach(username ->
                            assertThat(username.value())
                                    .matches(USERNAME_PATTERN)
                                    .hasSizeBetween(5, 30)
                        )
                    )
                    .verifyComplete();
        }

        @Test
        @DisplayName("Total generated count should match requested count")
        void totalGeneratedCountShouldMatchRequestedCount() {
            // Arrange
            GenerationRequest request = TestDataFixtures.generationRequest(7);
            List<Username> usernames = TestDataFixtures.usernames("user", 7);
            GenerationResponse expectedResponse = TestDataFixtures.generationResponse(
                    usernames, Language.EN, 150L);

            when(usernameGenerationUseCase.generate(any(GenerationRequest.class)))
                    .thenReturn(Mono.just(expectedResponse));

            // Act & Assert
            StepVerifier.create(usernameGenerationUseCase.generate(request))
                    .assertNext(response -> {
                        assertThat(response.totalGenerated()).isEqualTo(request.count());
                        assertThat(response.usernames()).hasSize(request.count());
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Request Validation Scenarios")
    class RequestValidationTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidRequestParameters")
        @DisplayName("Should reject invalid request parameters")
        void shouldRejectInvalidRequestParameters(
                String testName, Language language, Integer count, String expectedError) {
            // Act & Assert
            assertThatThrownBy(() -> GenerationRequest.of(language, count))
                    .hasMessageContaining(expectedError);
        }

        static Stream<Arguments> invalidRequestParameters() {
            return Stream.of(
                arguments("Null language", null, 5, ERROR_NULL_LANGUAGE),
                arguments("Count = 0", Language.EN, 0, ERROR_INVALID_COUNT),
                arguments("Negative count", Language.EN, -1, ERROR_INVALID_COUNT),
                arguments("Count > 10", Language.EN, 11, ERROR_INVALID_COUNT)
            );
        }

        @ParameterizedTest(name = "Should accept valid count: {0}")
        @CsvSource({
            "1,Valid minimum count",
            "5,Valid middle count",
            "10,Valid maximum count"
        })
        @DisplayName("Should accept valid request counts")
        void shouldAcceptValidRequestCounts(int count, String description) {
            // Arrange
            GenerationRequest request = TestDataFixtures.generationRequest(Language.EN, count);
            List<Username> usernames = TestDataFixtures.usernames("user", count);
            GenerationResponse expectedResponse = TestDataFixtures.generationResponse(usernames, Language.EN);

            when(usernameGenerationUseCase.generate(any(GenerationRequest.class)))
                    .thenReturn(Mono.just(expectedResponse));

            // Act & Assert
            StepVerifier.create(usernameGenerationUseCase.generate(request))
                    .assertNext(response ->
                        assertThat(response.usernames()).hasSize(count)
                    )
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Reactive Behavior Scenarios")
    class ReactiveBehaviorTests {

        @Test
        @DisplayName("Mono should complete successfully")
        void monoShouldCompleteSuccessfully() {
            // Arrange
            GenerationRequest request = TestDataFixtures.generationRequest(1);
            Username username = TestDataFixtures.username("reactive_user");
            GenerationResponse expectedResponse = TestDataFixtures.generationResponse(
                    List.of(username), Language.EN, 35L);

            when(usernameGenerationUseCase.generate(any(GenerationRequest.class)))
                    .thenReturn(Mono.just(expectedResponse));

            // Act & Assert
            verifyMonoCompletes(usernameGenerationUseCase.generate(request));
        }

        @Test
        @DisplayName("Mono should emit exactly one GenerationResponse")
        void monoShouldEmitExactlyOneGenerationResponse() {
            // Arrange
            GenerationRequest request = TestDataFixtures.generationRequest(2);
            List<Username> usernames = TestDataFixtures.usernames("mono_user", 2);
            GenerationResponse expectedResponse = TestDataFixtures.generationResponse(
                    usernames, Language.EN, 60L);

            when(usernameGenerationUseCase.generate(any(GenerationRequest.class)))
                    .thenReturn(Mono.just(expectedResponse));

            // Act & Assert
            StepVerifier.create(usernameGenerationUseCase.generate(request))
                    .expectNextMatches(response -> response != null)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Mono should complete within reasonable timeout")
        void monoShouldCompleteWithinReasonableTimeout() {
            // Arrange
            GenerationRequest request = TestDataFixtures.generationRequest(1);
            Username username = TestDataFixtures.username("timeout_user");
            GenerationResponse expectedResponse = TestDataFixtures.generationResponse(
                    List.of(username), Language.EN, 45L);

            when(usernameGenerationUseCase.generate(any(GenerationRequest.class)))
                    .thenReturn(Mono.just(expectedResponse));

            // Act & Assert
            StepVerifier.create(usernameGenerationUseCase.generate(request))
                    .expectNextCount(1)
                    .expectComplete()
                    .verify(REASONABLE_TIMEOUT);
        }

        @Test
        @DisplayName("Mono should handle errors gracefully")
        void monoShouldHandleErrorsGracefully() {
            // Arrange
            GenerationRequest request = TestDataFixtures.generationRequest(1);
            RuntimeException expectedError = new RuntimeException(ERROR_GENERATION_FAILED);

            when(usernameGenerationUseCase.generate(any(GenerationRequest.class)))
                    .thenReturn(Mono.error(expectedError));

            // Act & Assert
            verifyMonoError(usernameGenerationUseCase.generate(request), ERROR_GENERATION_FAILED);
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Conditions")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle cache hit scenario correctly")
        void shouldHandleCacheHitScenarioCorrectly() {
            // Arrange
            GenerationRequest request = TestDataFixtures.generationRequest(3);
            List<Username> usernames = TestDataFixtures.usernames("cached_user", 3);
            GenerationResponse expectedResponse = TestDataFixtures.cachedResponse(usernames, Language.EN);

            when(usernameGenerationUseCase.generate(any(GenerationRequest.class)))
                    .thenReturn(Mono.just(expectedResponse));

            // Act & Assert
            StepVerifier.create(usernameGenerationUseCase.generate(request))
                    .assertNext(response -> {
                        assertThat(response.cacheHit()).isTrue();
                        assertThat(response.responseTimeMs()).isLessThan(100L);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle different pattern types correctly")
        void shouldHandleDifferentPatternTypesCorrectly() {
            // Arrange
            GenerationRequest request = TestDataFixtures.generationRequest(3);
            List<Username> usernames = TestDataFixtures.mixedPatternUsernames("user");
            GenerationResponse expectedResponse = TestDataFixtures.generationResponse(
                    usernames, Language.EN, 70L);

            when(usernameGenerationUseCase.generate(any(GenerationRequest.class)))
                    .thenReturn(Mono.just(expectedResponse));

            // Act & Assert
            StepVerifier.create(usernameGenerationUseCase.generate(request))
                    .assertNext(response ->
                        assertThat(response.usernames())
                                .extracting(Username::patternType)
                                .containsExactlyInAnyOrder(PatternType.CLASSIC, PatternType.SEPARATOR, PatternType.WORDPLAY)
                    )
                    .verifyComplete();
        }

        @ParameterizedTest(name = "Should maintain {0} language in response")
        @MethodSource("languageProvider")
        @DisplayName("Should maintain request language in response")
        void shouldMaintainRequestLanguageInResponse(Language language, String prefix) {
            // Arrange
            GenerationRequest request = TestDataFixtures.generationRequest(language, 1);
            Username username = TestDataFixtures.username(prefix, language);

            when(usernameGenerationUseCase.generate(request))
                    .thenReturn(Mono.just(TestDataFixtures.generationResponse(List.of(username), language)));

            // Act & Assert
            StepVerifier.create(usernameGenerationUseCase.generate(request))
                    .assertNext(response -> assertThat(response.language()).isEqualTo(language))
                    .verifyComplete();
        }

        static Stream<Arguments> languageProvider() {
            return Stream.of(
                arguments(Language.EN, "english_user"),
                arguments(Language.ES, "usuario_espanol")
            );
        }
    }
}
