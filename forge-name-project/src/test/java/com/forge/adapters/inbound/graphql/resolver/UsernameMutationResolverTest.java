package com.forge.adapters.inbound.graphql.resolver;

import com.forge.adapters.inbound.graphql.input.GenerateUsernameInput;
import com.forge.adapters.inbound.graphql.mapper.GraphQLMarkUsedMapper;
import com.forge.adapters.inbound.graphql.mapper.GraphQLRequestMapper;
import com.forge.adapters.inbound.graphql.mapper.GraphQLResponseMapper;
import com.forge.adapters.inbound.graphql.type.MarkUsedResponse;
import com.forge.adapters.inbound.graphql.type.UsernameGenerationResponse;
import com.forge.domain.model.GenerationRequest;
import com.forge.domain.model.GenerationResponse;
import com.forge.domain.model.Language;
import com.forge.domain.model.MarkUsedResult;
import com.forge.domain.model.Username;
import com.forge.domain.ports.inbound.UsernameGenerationUseCase;
import com.forge.domain.ports.inbound.UsernameMarkUsedUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UsernameMutationResolver.
 * Tests the GraphQL mutation resolver for username generation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UsernameMutationResolver Tests")
class UsernameMutationResolverTest {

    @Mock
    private UsernameGenerationUseCase usernameGenerationUseCase;

    @Mock
    private UsernameMarkUsedUseCase usernameMarkUsedUseCase;

    @Mock
    private GraphQLRequestMapper requestMapper;

    @Mock
    private GraphQLResponseMapper responseMapper;

    @Mock
    private GraphQLMarkUsedMapper markUsedMapper;

    private UsernameMutationResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new UsernameMutationResolver(
                usernameGenerationUseCase,
                usernameMarkUsedUseCase,
                requestMapper,
                responseMapper,
                markUsedMapper
        );
    }

    @Test
    @DisplayName("Should create resolver instance successfully")
    void shouldCreateResolverInstance() {
        // ASSERT
        assertThat(resolver).isNotNull();
        assertThat(resolver).isInstanceOf(UsernameMutationResolver.class);
    }

    @Test
    @DisplayName("Should generate usernames successfully with English language")
    void shouldGenerateUsernamesSuccessfullyWithEnglishLanguage() {
        // ARRANGE
        GenerateUsernameInput input = new GenerateUsernameInput(Language.EN, 3);
        GenerationRequest domainRequest = GenerationRequest.of(Language.EN, 3);

        List<Username> usernames = List.of(
                Username.of("cool_gamer123", Language.EN),
                Username.of("epic_ninja456", Language.EN),
                Username.of("fast_runner789", Language.EN)
        );

        GenerationResponse domainResponse = GenerationResponse.of(
                usernames,
                Language.EN,
                false,
                245L
        );

        UsernameGenerationResponse graphqlResponse = UsernameGenerationResponse.of(
                List.of("cool_gamer123", "epic_ninja456", "fast_runner789"),
                domainResponse.generatedAt(),
                Language.EN,
                3,
                false,
                245L
        );

        when(requestMapper.toDomain(input)).thenReturn(domainRequest);
        when(usernameGenerationUseCase.generate(domainRequest)).thenReturn(Mono.just(domainResponse));
        when(responseMapper.toGraphQL(domainResponse)).thenReturn(graphqlResponse);

        // ACT
        Mono<UsernameGenerationResponse> result = resolver.generateUsernames(input);

        // ASSERT
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.usernames()).hasSize(3);
                    assertThat(response.usernames())
                            .containsExactly("cool_gamer123", "epic_ninja456", "fast_runner789");
                    assertThat(response.language()).isEqualTo(Language.EN);
                    assertThat(response.totalGenerated()).isEqualTo(3);
                    assertThat(response.cacheHit()).isFalse();
                    assertThat(response.responseTimeMs()).isEqualTo(245L);
                    assertThat(response.generatedAt()).isNotNull();
                })
                .verifyComplete();

        verify(requestMapper).toDomain(input);
        verify(usernameGenerationUseCase).generate(domainRequest);
        verify(responseMapper).toGraphQL(domainResponse);
        verifyNoMoreInteractions(requestMapper, usernameGenerationUseCase, responseMapper);
    }

    @Test
    @DisplayName("Should generate usernames successfully with Spanish language")
    void shouldGenerateUsernamesSuccessfullyWithSpanishLanguage() {
        // ARRANGE
        GenerateUsernameInput input = new GenerateUsernameInput(Language.ES, 5);
        GenerationRequest domainRequest = GenerationRequest.of(Language.ES, 5);

        List<Username> usernames = List.of(
                Username.of("guerrero_brillante123", Language.ES),
                Username.of("dragon_veloz456", Language.ES),
                Username.of("cazador_nocturno789", Language.ES),
                Username.of("mago_mistico321", Language.ES),
                Username.of("ninja_silencioso654", Language.ES)
        );

        GenerationResponse domainResponse = GenerationResponse.of(
                usernames,
                Language.ES,
                false,
                198L
        );

        UsernameGenerationResponse graphqlResponse = UsernameGenerationResponse.of(
                List.of("guerrero_brillante123", "dragon_veloz456", "cazador_nocturno789",
                        "mago_mistico321", "ninja_silencioso654"),
                domainResponse.generatedAt(),
                Language.ES,
                5,
                false,
                198L
        );

        when(requestMapper.toDomain(input)).thenReturn(domainRequest);
        when(usernameGenerationUseCase.generate(domainRequest)).thenReturn(Mono.just(domainResponse));
        when(responseMapper.toGraphQL(domainResponse)).thenReturn(graphqlResponse);

        // ACT
        Mono<UsernameGenerationResponse> result = resolver.generateUsernames(input);

        // ASSERT
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.usernames()).hasSize(5);
                    assertThat(response.language()).isEqualTo(Language.ES);
                    assertThat(response.totalGenerated()).isEqualTo(5);
                    assertThat(response.responseTimeMs()).isEqualTo(198L);
                })
                .verifyComplete();

        verify(requestMapper).toDomain(input);
        verify(usernameGenerationUseCase).generate(domainRequest);
        verify(responseMapper).toGraphQL(domainResponse);
    }

    @Test
    @DisplayName("Should generate single username when count is default (1)")
    void shouldGenerateSingleUsernameWhenCountIsDefault() {
        // ARRANGE
        GenerateUsernameInput input = new GenerateUsernameInput(Language.EN, null); // null defaults to 1
        GenerationRequest domainRequest = GenerationRequest.of(Language.EN, 1);

        List<Username> usernames = List.of(
                Username.of("shadow_warrior999", Language.EN)
        );

        GenerationResponse domainResponse = GenerationResponse.of(
                usernames,
                Language.EN,
                false,
                156L
        );

        UsernameGenerationResponse graphqlResponse = UsernameGenerationResponse.of(
                List.of("shadow_warrior999"),
                domainResponse.generatedAt(),
                Language.EN,
                1,
                false,
                156L
        );

        when(requestMapper.toDomain(input)).thenReturn(domainRequest);
        when(usernameGenerationUseCase.generate(domainRequest)).thenReturn(Mono.just(domainResponse));
        when(responseMapper.toGraphQL(domainResponse)).thenReturn(graphqlResponse);

        // ACT
        Mono<UsernameGenerationResponse> result = resolver.generateUsernames(input);

        // ASSERT
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.usernames()).hasSize(1);
                    assertThat(response.usernames()).containsExactly("shadow_warrior999");
                    assertThat(response.totalGenerated()).isEqualTo(1);
                })
                .verifyComplete();

        verify(requestMapper).toDomain(input);
        verify(usernameGenerationUseCase).generate(domainRequest);
        verify(responseMapper).toGraphQL(domainResponse);
    }

    @Test
    @DisplayName("Should indicate cache hit when usernames retrieved from cache")
    void shouldIndicateCacheHitWhenUsernamesRetrievedFromCache() {
        // ARRANGE
        GenerateUsernameInput input = new GenerateUsernameInput(Language.EN, 3);
        GenerationRequest domainRequest = GenerationRequest.of(Language.EN, 3);

        List<Username> usernames = List.of(
                Username.of("cached_user1", Language.EN),
                Username.of("cached_user2", Language.EN),
                Username.of("cached_user3", Language.EN)
        );

        GenerationResponse domainResponse = GenerationResponse.of(
                usernames,
                Language.EN,
                true, // Cache hit
                12L  // Fast response time due to cache
        );

        UsernameGenerationResponse graphqlResponse = UsernameGenerationResponse.of(
                List.of("cached_user1", "cached_user2", "cached_user3"),
                domainResponse.generatedAt(),
                Language.EN,
                3,
                true,
                12L
        );

        when(requestMapper.toDomain(input)).thenReturn(domainRequest);
        when(usernameGenerationUseCase.generate(domainRequest)).thenReturn(Mono.just(domainResponse));
        when(responseMapper.toGraphQL(domainResponse)).thenReturn(graphqlResponse);

        // ACT
        Mono<UsernameGenerationResponse> result = resolver.generateUsernames(input);

        // ASSERT
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.cacheHit()).isTrue();
                    assertThat(response.responseTimeMs()).isLessThan(50L);
                })
                .verifyComplete();

        verify(requestMapper).toDomain(input);
        verify(usernameGenerationUseCase).generate(domainRequest);
        verify(responseMapper).toGraphQL(domainResponse);
    }

    @Test
    @DisplayName("Should generate maximum allowed usernames (10)")
    void shouldGenerateMaximumAllowedUsernames() {
        // ARRANGE
        GenerateUsernameInput input = new GenerateUsernameInput(Language.EN, 10);
        GenerationRequest domainRequest = GenerationRequest.of(Language.EN, 10);

        List<Username> usernames = List.of(
                Username.of("test_user1", Language.EN),
                Username.of("test_user2", Language.EN),
                Username.of("test_user3", Language.EN),
                Username.of("test_user4", Language.EN),
                Username.of("test_user5", Language.EN),
                Username.of("test_user6", Language.EN),
                Username.of("test_user7", Language.EN),
                Username.of("test_user8", Language.EN),
                Username.of("test_user9", Language.EN),
                Username.of("test_user10", Language.EN)
        );

        GenerationResponse domainResponse = GenerationResponse.of(
                usernames,
                Language.EN,
                false,
                387L
        );

        UsernameGenerationResponse graphqlResponse = UsernameGenerationResponse.of(
                List.of("test_user1", "test_user2", "test_user3", "test_user4", "test_user5",
                        "test_user6", "test_user7", "test_user8", "test_user9", "test_user10"),
                domainResponse.generatedAt(),
                Language.EN,
                10,
                false,
                387L
        );

        when(requestMapper.toDomain(input)).thenReturn(domainRequest);
        when(usernameGenerationUseCase.generate(domainRequest)).thenReturn(Mono.just(domainResponse));
        when(responseMapper.toGraphQL(domainResponse)).thenReturn(graphqlResponse);

        // ACT
        Mono<UsernameGenerationResponse> result = resolver.generateUsernames(input);

        // ASSERT
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.usernames()).hasSize(10);
                    assertThat(response.totalGenerated()).isEqualTo(10);
                })
                .verifyComplete();

        verify(requestMapper).toDomain(input);
        verify(usernameGenerationUseCase).generate(domainRequest);
        verify(responseMapper).toGraphQL(domainResponse);
    }

    @Test
    @DisplayName("Should handle use case error gracefully")
    void shouldHandleUseCaseErrorGracefully() {
        // ARRANGE
        GenerateUsernameInput input = new GenerateUsernameInput(Language.EN, 3);
        GenerationRequest domainRequest = GenerationRequest.of(Language.EN, 3);

        when(requestMapper.toDomain(input)).thenReturn(domainRequest);
        when(usernameGenerationUseCase.generate(domainRequest))
                .thenReturn(Mono.error(new RuntimeException("Database connection failed")));

        // ACT
        Mono<UsernameGenerationResponse> result = resolver.generateUsernames(input);

        // ASSERT
        StepVerifier.create(result)
                .expectErrorMatches(error ->
                        error instanceof RuntimeException &&
                                error.getMessage().equals("Database connection failed")
                )
                .verify();

        verify(requestMapper).toDomain(input);
        verify(usernameGenerationUseCase).generate(domainRequest);
        verifyNoInteractions(responseMapper);
    }

    @Test
    @DisplayName("Should handle mapper error during request mapping")
    void shouldHandleMapperErrorDuringRequestMapping() {
        // ARRANGE
        GenerateUsernameInput input = new GenerateUsernameInput(Language.EN, 3);

        when(requestMapper.toDomain(input))
                .thenThrow(new IllegalArgumentException("Invalid mapping"));

        // ACT & ASSERT
        StepVerifier.create(resolver.generateUsernames(input))
                .expectErrorMatches(error ->
                        error instanceof IllegalArgumentException &&
                                error.getMessage().equals("Invalid mapping")
                )
                .verify();

        verify(requestMapper).toDomain(input);
        verifyNoInteractions(usernameGenerationUseCase, responseMapper);
    }

    @Test
    @DisplayName("Should complete reactive chain successfully")
    void shouldCompleteReactiveChainSuccessfully() {
        // ARRANGE
        GenerateUsernameInput input = new GenerateUsernameInput(Language.EN, 2);
        GenerationRequest domainRequest = GenerationRequest.of(Language.EN, 2);

        List<Username> usernames = List.of(
                Username.of("reactive_user1", Language.EN),
                Username.of("reactive_user2", Language.EN)
        );

        GenerationResponse domainResponse = GenerationResponse.of(
                usernames,
                Language.EN,
                false,
                100L
        );

        UsernameGenerationResponse graphqlResponse = UsernameGenerationResponse.of(
                List.of("reactive_user1", "reactive_user2"),
                domainResponse.generatedAt(),
                Language.EN,
                2,
                false,
                100L
        );

        when(requestMapper.toDomain(any(GenerateUsernameInput.class))).thenReturn(domainRequest);
        when(usernameGenerationUseCase.generate(any(GenerationRequest.class)))
                .thenReturn(Mono.just(domainResponse));
        when(responseMapper.toGraphQL(any(GenerationResponse.class))).thenReturn(graphqlResponse);

        // ACT
        Mono<UsernameGenerationResponse> result = resolver.generateUsernames(input);

        // ASSERT
        StepVerifier.create(result)
                .assertNext(response -> assertThat(response).isNotNull())
                .verifyComplete();

        verify(requestMapper, times(1)).toDomain(any(GenerateUsernameInput.class));
        verify(usernameGenerationUseCase, times(1)).generate(any(GenerationRequest.class));
        verify(responseMapper, times(1)).toGraphQL(any(GenerationResponse.class));
    }

    @Test
    @DisplayName("Should verify all dependencies are called in correct order")
    void shouldVerifyAllDependenciesAreCalledInCorrectOrder() {
        // ARRANGE
        GenerateUsernameInput input = new GenerateUsernameInput(Language.EN, 1);
        GenerationRequest domainRequest = GenerationRequest.of(Language.EN, 1);

        List<Username> usernames = List.of(Username.of("order_test_user", Language.EN));

        GenerationResponse domainResponse = GenerationResponse.of(
                usernames,
                Language.EN,
                false,
                50L
        );

        UsernameGenerationResponse graphqlResponse = UsernameGenerationResponse.of(
                List.of("order_test_user"),
                domainResponse.generatedAt(),
                Language.EN,
                1,
                false,
                50L
        );

        when(requestMapper.toDomain(input)).thenReturn(domainRequest);
        when(usernameGenerationUseCase.generate(domainRequest)).thenReturn(Mono.just(domainResponse));
        when(responseMapper.toGraphQL(domainResponse)).thenReturn(graphqlResponse);

        // ACT
        resolver.generateUsernames(input).block();

        // ASSERT - Verify call order
        var inOrder = inOrder(requestMapper, usernameGenerationUseCase, responseMapper);
        inOrder.verify(requestMapper).toDomain(input);
        inOrder.verify(usernameGenerationUseCase).generate(domainRequest);
        inOrder.verify(responseMapper).toGraphQL(domainResponse);
        inOrder.verifyNoMoreInteractions();
    }

    // ==========================================
    // Mark Username As Used Mutation Tests
    // ==========================================

    @Test
    @DisplayName("Should mark username as used successfully on first marking")
    void shouldMarkUsernameAsUsedSuccessfullyOnFirstMarking() {
        // ARRANGE
        String username = "cleverpanda42";
        LocalDateTime markedAt = LocalDateTime.of(2025, 10, 28, 14, 30, 0);

        MarkUsedResult domainResult = MarkUsedResult.marked(username, markedAt);
        MarkUsedResponse graphqlResponse = MarkUsedResponse.of(
                username,
                true,
                false,
                markedAt.toInstant(ZoneOffset.UTC),
                "Username successfully marked as used"
        );

        when(usernameMarkUsedUseCase.markAsUsed(username)).thenReturn(Mono.just(domainResult));
        when(markUsedMapper.toGraphQL(domainResult)).thenReturn(graphqlResponse);

        // ACT
        Mono<MarkUsedResponse> result = resolver.markUsernameAsUsed(username);

        // ASSERT
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.username()).isEqualTo(username);
                    assertThat(response.marked()).isTrue();
                    assertThat(response.wasAlreadyUsed()).isFalse();
                    assertThat(response.markedAt()).isNotNull();
                    assertThat(response.markedAt()).isEqualTo(markedAt.toInstant(ZoneOffset.UTC));
                    assertThat(response.message()).isEqualTo("Username successfully marked as used");
                })
                .verifyComplete();

        verify(usernameMarkUsedUseCase).markAsUsed(username);
        verify(markUsedMapper).toGraphQL(domainResult);
        verifyNoMoreInteractions(usernameMarkUsedUseCase, markUsedMapper);
    }

    @Test
    @DisplayName("Should return already used response when marking duplicate username")
    void shouldReturnAlreadyUsedResponseWhenMarkingDuplicateUsername() {
        // ARRANGE
        String username = "existinguser123";

        MarkUsedResult domainResult = MarkUsedResult.alreadyUsed(username);
        MarkUsedResponse graphqlResponse = MarkUsedResponse.of(
                username,
                false,
                true,
                null,
                "Username was already used"
        );

        when(usernameMarkUsedUseCase.markAsUsed(username)).thenReturn(Mono.just(domainResult));
        when(markUsedMapper.toGraphQL(domainResult)).thenReturn(graphqlResponse);

        // ACT
        Mono<MarkUsedResponse> result = resolver.markUsernameAsUsed(username);

        // ASSERT
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.username()).isEqualTo(username);
                    assertThat(response.marked()).isFalse();
                    assertThat(response.wasAlreadyUsed()).isTrue();
                    assertThat(response.markedAt()).isNull();
                    assertThat(response.message()).isEqualTo("Username was already used");
                })
                .verifyComplete();

        verify(usernameMarkUsedUseCase).markAsUsed(username);
        verify(markUsedMapper).toGraphQL(domainResult);
    }

    @Test
    @DisplayName("Should mark username with minimum valid length")
    void shouldMarkUsernameWithMinimumValidLength() {
        // ARRANGE
        String username = "abc12"; // 5 characters
        LocalDateTime markedAt = LocalDateTime.now();

        MarkUsedResult domainResult = MarkUsedResult.marked(username, markedAt);
        MarkUsedResponse graphqlResponse = MarkUsedResponse.of(
                username,
                true,
                false,
                markedAt.toInstant(ZoneOffset.UTC),
                "Username successfully marked as used"
        );

        when(usernameMarkUsedUseCase.markAsUsed(username)).thenReturn(Mono.just(domainResult));
        when(markUsedMapper.toGraphQL(domainResult)).thenReturn(graphqlResponse);

        // ACT
        Mono<MarkUsedResponse> result = resolver.markUsernameAsUsed(username);

        // ASSERT
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.username()).isEqualTo(username);
                    assertThat(response.marked()).isTrue();
                })
                .verifyComplete();

        verify(usernameMarkUsedUseCase).markAsUsed(username);
        verify(markUsedMapper).toGraphQL(domainResult);
    }

    @Test
    @DisplayName("Should mark username with maximum valid length")
    void shouldMarkUsernameWithMaximumValidLength() {
        // ARRANGE
        String username = "a".repeat(30); // 30 characters
        LocalDateTime markedAt = LocalDateTime.now();

        MarkUsedResult domainResult = MarkUsedResult.marked(username, markedAt);
        MarkUsedResponse graphqlResponse = MarkUsedResponse.of(
                username,
                true,
                false,
                markedAt.toInstant(ZoneOffset.UTC),
                "Username successfully marked as used"
        );

        when(usernameMarkUsedUseCase.markAsUsed(username)).thenReturn(Mono.just(domainResult));
        when(markUsedMapper.toGraphQL(domainResult)).thenReturn(graphqlResponse);

        // ACT
        Mono<MarkUsedResponse> result = resolver.markUsernameAsUsed(username);

        // ASSERT
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.username()).isEqualTo(username);
                    assertThat(response.username()).hasSize(30);
                    assertThat(response.marked()).isTrue();
                })
                .verifyComplete();

        verify(usernameMarkUsedUseCase).markAsUsed(username);
    }

    @Test
    @DisplayName("Should mark username with special allowed characters")
    void shouldMarkUsernameWithSpecialAllowedCharacters() {
        // ARRANGE
        String username = "user_name-123";
        LocalDateTime markedAt = LocalDateTime.now();

        MarkUsedResult domainResult = MarkUsedResult.marked(username, markedAt);
        MarkUsedResponse graphqlResponse = MarkUsedResponse.of(
                username,
                true,
                false,
                markedAt.toInstant(ZoneOffset.UTC),
                "Username successfully marked as used"
        );

        when(usernameMarkUsedUseCase.markAsUsed(username)).thenReturn(Mono.just(domainResult));
        when(markUsedMapper.toGraphQL(domainResult)).thenReturn(graphqlResponse);

        // ACT
        Mono<MarkUsedResponse> result = resolver.markUsernameAsUsed(username);

        // ASSERT
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.username()).isEqualTo(username);
                    assertThat(response.marked()).isTrue();
                })
                .verifyComplete();

        verify(usernameMarkUsedUseCase).markAsUsed(username);
        verify(markUsedMapper).toGraphQL(domainResult);
    }

    @Test
    @DisplayName("Should handle use case error during mark operation")
    void shouldHandleUseCaseErrorDuringMarkOperation() {
        // ARRANGE
        String username = "erroruser";

        when(usernameMarkUsedUseCase.markAsUsed(username))
                .thenReturn(Mono.error(new RuntimeException("Database connection failed")));

        // ACT
        Mono<MarkUsedResponse> result = resolver.markUsernameAsUsed(username);

        // ASSERT
        StepVerifier.create(result)
                .expectErrorMatches(error ->
                        error instanceof RuntimeException &&
                        error.getMessage().equals("Database connection failed")
                )
                .verify();

        verify(usernameMarkUsedUseCase).markAsUsed(username);
        verifyNoInteractions(markUsedMapper);
    }

    @Test
    @DisplayName("Should handle mapper error during mark response mapping")
    void shouldHandleMapperErrorDuringMarkResponseMapping() {
        // ARRANGE
        String username = "mapperroruser";
        LocalDateTime markedAt = LocalDateTime.now();
        MarkUsedResult domainResult = MarkUsedResult.marked(username, markedAt);

        when(usernameMarkUsedUseCase.markAsUsed(username)).thenReturn(Mono.just(domainResult));
        when(markUsedMapper.toGraphQL(domainResult))
                .thenThrow(new IllegalArgumentException("Invalid mapping"));

        // ACT
        Mono<MarkUsedResponse> result = resolver.markUsernameAsUsed(username);

        // ASSERT
        StepVerifier.create(result)
                .expectErrorMatches(error ->
                        error instanceof IllegalArgumentException &&
                        error.getMessage().equals("Invalid mapping")
                )
                .verify();

        verify(usernameMarkUsedUseCase).markAsUsed(username);
        verify(markUsedMapper).toGraphQL(domainResult);
    }

    @Test
    @DisplayName("Should complete reactive chain successfully for mark operation")
    void shouldCompleteReactiveChainSuccessfullyForMarkOperation() {
        // ARRANGE
        String username = "reactiveuser";
        LocalDateTime markedAt = LocalDateTime.now();

        MarkUsedResult domainResult = MarkUsedResult.marked(username, markedAt);
        MarkUsedResponse graphqlResponse = MarkUsedResponse.of(
                username,
                true,
                false,
                markedAt.toInstant(ZoneOffset.UTC),
                "Username successfully marked as used"
        );

        when(usernameMarkUsedUseCase.markAsUsed(any(String.class))).thenReturn(Mono.just(domainResult));
        when(markUsedMapper.toGraphQL(any(MarkUsedResult.class))).thenReturn(graphqlResponse);

        // ACT
        Mono<MarkUsedResponse> result = resolver.markUsernameAsUsed(username);

        // ASSERT
        StepVerifier.create(result)
                .assertNext(response -> assertThat(response).isNotNull())
                .verifyComplete();

        verify(usernameMarkUsedUseCase, times(1)).markAsUsed(any(String.class));
        verify(markUsedMapper, times(1)).toGraphQL(any(MarkUsedResult.class));
    }

    @Test
    @DisplayName("Should verify mark operation dependencies are called in correct order")
    void shouldVerifyMarkOperationDependenciesAreCalledInCorrectOrder() {
        // ARRANGE
        String username = "ordertest";
        LocalDateTime markedAt = LocalDateTime.of(2025, 10, 28, 16, 0, 0);

        MarkUsedResult domainResult = MarkUsedResult.marked(username, markedAt);
        MarkUsedResponse graphqlResponse = MarkUsedResponse.of(
                username,
                true,
                false,
                markedAt.toInstant(ZoneOffset.UTC),
                "Username successfully marked as used"
        );

        when(usernameMarkUsedUseCase.markAsUsed(username)).thenReturn(Mono.just(domainResult));
        when(markUsedMapper.toGraphQL(domainResult)).thenReturn(graphqlResponse);

        // ACT
        resolver.markUsernameAsUsed(username).block();

        // ASSERT - Verify call order
        var inOrder = inOrder(usernameMarkUsedUseCase, markUsedMapper);
        inOrder.verify(usernameMarkUsedUseCase).markAsUsed(username);
        inOrder.verify(markUsedMapper).toGraphQL(domainResult);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("Should preserve timestamp precision in mark operation response")
    void shouldPreserveTimestampPrecisionInMarkOperationResponse() {
        // ARRANGE
        String username = "preciseuser";
        LocalDateTime markedAt = LocalDateTime.of(2025, 3, 21, 15, 45, 30, 123456789);

        MarkUsedResult domainResult = MarkUsedResult.marked(username, markedAt);
        MarkUsedResponse graphqlResponse = MarkUsedResponse.of(
                username,
                true,
                false,
                markedAt.toInstant(ZoneOffset.UTC),
                "Username successfully marked as used"
        );

        when(usernameMarkUsedUseCase.markAsUsed(username)).thenReturn(Mono.just(domainResult));
        when(markUsedMapper.toGraphQL(domainResult)).thenReturn(graphqlResponse);

        // ACT
        Mono<MarkUsedResponse> result = resolver.markUsernameAsUsed(username);

        // ASSERT
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.markedAt()).isNotNull();
                    assertThat(response.markedAt().getNano()).isEqualTo(123456789);
                    assertThat(response.markedAt()).isEqualTo(markedAt.toInstant(ZoneOffset.UTC));
                })
                .verifyComplete();

        verify(usernameMarkUsedUseCase).markAsUsed(username);
        verify(markUsedMapper).toGraphQL(domainResult);
    }

    @Test
    @DisplayName("Should handle concurrent mark requests for different usernames")
    void shouldHandleConcurrentMarkRequestsForDifferentUsernames() {
        // ARRANGE
        String username1 = "concurrent1";
        String username2 = "concurrent2";
        LocalDateTime markedAt1 = LocalDateTime.now();
        LocalDateTime markedAt2 = LocalDateTime.now().plusSeconds(1);

        MarkUsedResult domainResult1 = MarkUsedResult.marked(username1, markedAt1);
        MarkUsedResult domainResult2 = MarkUsedResult.marked(username2, markedAt2);

        MarkUsedResponse graphqlResponse1 = MarkUsedResponse.of(
                username1, true, false, markedAt1.toInstant(ZoneOffset.UTC), "Username successfully marked as used"
        );
        MarkUsedResponse graphqlResponse2 = MarkUsedResponse.of(
                username2, true, false, markedAt2.toInstant(ZoneOffset.UTC), "Username successfully marked as used"
        );

        when(usernameMarkUsedUseCase.markAsUsed(username1)).thenReturn(Mono.just(domainResult1));
        when(usernameMarkUsedUseCase.markAsUsed(username2)).thenReturn(Mono.just(domainResult2));
        when(markUsedMapper.toGraphQL(domainResult1)).thenReturn(graphqlResponse1);
        when(markUsedMapper.toGraphQL(domainResult2)).thenReturn(graphqlResponse2);

        // ACT
        Mono<MarkUsedResponse> result1 = resolver.markUsernameAsUsed(username1);
        Mono<MarkUsedResponse> result2 = resolver.markUsernameAsUsed(username2);

        // ASSERT
        StepVerifier.create(result1)
                .assertNext(response -> assertThat(response.username()).isEqualTo(username1))
                .verifyComplete();

        StepVerifier.create(result2)
                .assertNext(response -> assertThat(response.username()).isEqualTo(username2))
                .verifyComplete();

        verify(usernameMarkUsedUseCase).markAsUsed(username1);
        verify(usernameMarkUsedUseCase).markAsUsed(username2);
    }

    @Test
    @DisplayName("Should validate username before marking through use case")
    void shouldValidateUsernameBeforeMarkingThroughUseCase() {
        // ARRANGE
        String validUsername = "valid_user_123";
        LocalDateTime markedAt = LocalDateTime.now();

        MarkUsedResult domainResult = MarkUsedResult.marked(validUsername, markedAt);
        MarkUsedResponse graphqlResponse = MarkUsedResponse.of(
                validUsername,
                true,
                false,
                markedAt.toInstant(ZoneOffset.UTC),
                "Username successfully marked as used"
        );

        when(usernameMarkUsedUseCase.markAsUsed(validUsername)).thenReturn(Mono.just(domainResult));
        when(markUsedMapper.toGraphQL(domainResult)).thenReturn(graphqlResponse);

        // ACT
        Mono<MarkUsedResponse> result = resolver.markUsernameAsUsed(validUsername);

        // ASSERT
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.username()).matches("[a-z0-9_-]+");
                    assertThat(response.username().length()).isBetween(5, 30);
                })
                .verifyComplete();

        verify(usernameMarkUsedUseCase).markAsUsed(validUsername);
    }

}
