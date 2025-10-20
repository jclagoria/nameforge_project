package com.forge.domain.usecases;

import com.forge.domain.model.*;
import com.forge.domain.ports.inbound.UsernameGenerationUseCase;
import com.forge.domain.ports.outboung.CacheService;
import com.forge.domain.ports.outboung.GeneratorService;
import com.forge.domain.ports.outboung.ModerationService;
import com.forge.domain.ports.outboung.UsernameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UsernameGenerationUseCaseImpl Tests")
class UsernameGenerationUseCaseImplTest {

    @Mock
    private UsernameRepository usernameRepository;

    @Mock
    private CacheService cacheService;

    @Mock
    private GeneratorService generatorService;

    @Mock
    private ModerationService moderationService;

    private UsernameGenerationUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UsernameGenerationUseCaseImpl(
                usernameRepository,
                cacheService,
                generatorService,
                moderationService
        );
    }

    @Test
    @DisplayName("Should create an UseCase Instance")
    void shouldCreateUseCaseInstance() {
        // ASSERT
        assertThat(useCase).isNotNull();
        assertThat(useCase).isInstanceOf(UsernameGenerationUseCase.class);
    }

    @Test
    @DisplayName("Should Generate Usernames form Cache")
    void shouldGenerateUsernamesFromCache() {
        // ARRANGE
        GenerationRequest request = GenerationRequest.of(Language.EN, 3);

        Username username1 = Username.of("cleverpanda42", Language.EN);
        Username username2 = Username.of("swifteagle99", Language.EN);
        Username username3 = Username.of("brightfox88", Language.EN);

        when(cacheService.getCachedUsernames(Language.EN, 3))
                .thenReturn(Flux.just(username1, username2, username3));

        // ACT
        Mono<GenerationResponse> result = useCase.generate(request);

        // ASSERT
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.usernames()).hasSize(3);
                    assertThat(response.language()).isEqualTo(Language.EN);
                    assertThat(response.cacheHit()).isTrue();
                    assertThat(response.getUsernameValues())
                            .containsExactly("cleverpanda42", "swifteagle99", "brightfox88");
                })
                .verifyComplete();

        verify(cacheService).getCachedUsernames(Language.EN, 3);
        verifyNoInteractions(generatorService, moderationService, usernameRepository);
    }

    @Test
    @DisplayName("Should Generate new usernames when cache is empty")
    void shouldGenerateNewUsernamesWhenCacheEmpty() {
        // ARRANGE
        GenerationRequest request = GenerationRequest.of(Language.EN, 2);

        when(cacheService.getCachedUsernames(Language.EN, 2))
                .thenReturn(Flux.empty());

        // Implementation uses PatternType.selectRandom(), so we need to match any PatternType
        when(generatorService.generateUsername(eq(Language.EN), any(PatternType.class)))
                .thenReturn(Mono.just("cleverpanda42"))
                .thenReturn(Mono.just("swifteagle99"))
                .thenReturn(Mono.just("extra1"))
                .thenReturn(Mono.just("extra2"));

        when(cacheService.mightExist(anyString()))
                .thenReturn(Mono.just(false));

        when(moderationService.isAppropriate(anyString()))
                .thenReturn(Mono.just(true));

        when(usernameRepository.saveBatch(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(cacheService.cacheUsernames(any(Language.class), any(Flux.class)))
                .thenReturn(Mono.empty());

        // ACT
        Mono<GenerationResponse> result = useCase.generate(request);

        // ASSERT
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.usernames()).hasSize(2);
                    assertThat(response.cacheHit()).isFalse();
                })
                .verifyComplete();

        verify(generatorService, atLeastOnce())
                .generateUsername(eq(Language.EN), any(PatternType.class));
        verify(moderationService, atLeast(2)).isAppropriate(anyString());
        verify(usernameRepository).saveBatch(any());
    }

    @Test
    @DisplayName("Should Skip duplicate usernames")
    void shouldSkipDuplicateUsernames() {
        // ARRANGE
        GenerationRequest request = GenerationRequest.of(Language.EN, 2);

        when(cacheService.getCachedUsernames(Language.EN, 2))
                .thenReturn(Flux.empty());

        // Implementation uses PatternType.selectRandom(), need more usernames due to GENERATION_MULTIPLIER=2
        // Provide enough return values: duplicate will be filtered, need extras for the multiplier
        when(generatorService.generateUsername(eq(Language.EN), any(PatternType.class)))
                .thenReturn(Mono.just("duplicate42"))
                .thenReturn(Mono.just("cleverpanda42"))
                .thenReturn(Mono.just("swifteagle99"))
                .thenReturn(Mono.just("extra1"))
                .thenReturn(Mono.just("extra2"))
                .thenReturn(Mono.just("extra3"))
                .thenReturn(Mono.just("extra4"))
                .thenReturn(Mono.just("extra5"));

        when(cacheService.mightExist("duplicate42"))
                .thenReturn(Mono.just(true));
        when(usernameRepository.existsByUsername("duplicate42"))
                .thenReturn(Mono.just(true));  // Already exists

        when(cacheService.mightExist(argThat(arg -> !arg.equals("duplicate42"))))
                .thenReturn(Mono.just(false));

        when(moderationService.isAppropriate(anyString()))
                .thenReturn(Mono.just(true));

        when(usernameRepository.saveBatch(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(cacheService.cacheUsernames(any(Language.class), any(Flux.class)))
                .thenReturn(Mono.empty());

        // ACT
        Mono<GenerationResponse> result = useCase.generate(request);

        // ASSERT
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.usernames()).hasSize(2);
                    assertThat(response.getUsernameValues())
                            .doesNotContain("duplicate42");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Filter inappropriate content")
    void shouldFilterInappropriateUsernames() {
        // ARRANGE
        GenerationRequest request = GenerationRequest.of(Language.EN, 2);

        when(cacheService.getCachedUsernames(Language.EN, 2))
                .thenReturn(Flux.empty());

        when(generatorService.generateUsername(eq(Language.EN), any(PatternType.class)))
                .thenReturn(Mono.just("badword42"))
                .thenReturn(Mono.just("cleverpanda42"))
                .thenReturn(Mono.just("swifteagle99"))
                .thenReturn(Mono.just("extra1"))
                .thenReturn(Mono.just("extra2"))
                .thenReturn(Mono.just("extra3"))
                .thenReturn(Mono.just("extra4"))
                .thenReturn(Mono.just("extra5"));

        when(cacheService.mightExist(anyString()))
                .thenReturn(Mono.just(false));

        // First username is inappropriate, others are appropriate
        when(moderationService.isAppropriate("badword42"))
                .thenReturn(Mono.just(false));
        when(moderationService.isAppropriate(argThat(arg -> !arg.equals("badword42"))))
                .thenReturn(Mono.just(true));

        when(usernameRepository.saveBatch(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(cacheService.cacheUsernames(any(Language.class), any(Flux.class)))
                .thenReturn(Mono.empty());

        // ACT
        Mono<GenerationResponse> result = useCase.generate(request);

        // ASSERT
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.usernames()).hasSize(2);
                    assertThat(response.getUsernameValues())
                            .doesNotContain("badword42");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle GeneratorService Failure")
    void shouldHandleGeneratorServiceFailure() {
        // ARRANGE
        GenerationRequest request = GenerationRequest.of(Language.EN, 1);

        when(cacheService.getCachedUsernames(Language.EN, 1))
                .thenReturn(Flux.empty());

        when(generatorService.generateUsername(eq(Language.EN), any(PatternType.class)))
                .thenReturn(Mono.error(new RuntimeException("Generator service unavailable")));

        // ACT
        Mono<GenerationResponse> result = useCase.generate(request);

        // ASSERT
        StepVerifier.create(result)
                .expectErrorMessage("Generator service unavailable")
                .verify();
    }

    @Test
    @DisplayName("Should Handle moderation service timeout with fallback")
    void shouldFallbackWhenModerationServiceTimesOut() {
        // ARRANGE
        GenerationRequest request = GenerationRequest.of(Language.EN, 1);

        when(cacheService.getCachedUsernames(Language.EN, 1))
                .thenReturn(Flux.empty());

        when(generatorService.generateUsername(eq(Language.EN), any(PatternType.class)))
                .thenReturn(Mono.just("cleverpanda42"))
                .thenReturn(Mono.just("extra1"));

        when(cacheService.mightExist(anyString()))
                .thenReturn(Mono.just(false));

        // Moderation service times out or errors - implementation has fallback to approve (true)
        when(moderationService.isAppropriate(anyString()))
                .thenReturn(Mono.delay(Duration.ofSeconds(10))
                        .map(l -> true));

        when(usernameRepository.saveBatch(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(cacheService.cacheUsernames(any(Language.class), any(Flux.class)))
                .thenReturn(Mono.empty());

        // ACT
        Mono<GenerationResponse> result = useCase.generate(request);

        // ASSERT - should complete successfully due to fallback behavior
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.usernames()).hasSize(1);
                    assertThat(response.getUsernameValues()).contains("cleverpanda42");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Save Usernames In Batch")
    void shouldSaveUsernamesInBatch() {
        // ARRANGE
        GenerationRequest request = GenerationRequest.of(Language.EN, 5);

        when(cacheService.getCachedUsernames(Language.EN, 5))
                .thenReturn(Flux.empty());

        when(generatorService.generateUsername(eq(Language.EN), any(PatternType.class)))
                .thenReturn(Mono.just("user1"))
                .thenReturn(Mono.just("user2"))
                .thenReturn(Mono.just("user3"))
                .thenReturn(Mono.just("user4"))
                .thenReturn(Mono.just("user5"))
                .thenReturn(Mono.just("user6"))
                .thenReturn(Mono.just("user7"))
                .thenReturn(Mono.just("user8"))
                .thenReturn(Mono.just("user9"))
                .thenReturn(Mono.just("user10"));

        when(cacheService.mightExist(anyString()))
                .thenReturn(Mono.just(false));

        when(moderationService.isAppropriate(anyString()))
                .thenReturn(Mono.just(true));

        when(usernameRepository.saveBatch(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(cacheService.cacheUsernames(any(Language.class), any(Flux.class)))
                .thenReturn(Mono.empty());

        // ACT
        StepVerifier.create(useCase.generate(request))
                .assertNext(response ->
                        assertThat(response.usernames()).hasSize(5))
                .verifyComplete();

        // ASSERT
        verify(usernameRepository).saveBatch(any());
    }
}
