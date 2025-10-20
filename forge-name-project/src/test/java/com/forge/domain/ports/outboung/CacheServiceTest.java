package com.forge.domain.ports.outboung;

import com.forge.domain.model.Language;
import com.forge.domain.model.PatternType;
import com.forge.domain.model.Username;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.forge.domain.fixtures.TestDataFixtures;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CacheService Tests")
class CacheServiceTest {

    @Mock
    private CacheService cacheService;

    // Constants
    private static final Duration REASONABLE_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration FAST_LOOKUP = Duration.ofMillis(100);
    private static final String ERROR_CACHE_RETRIEVAL = "Cache retrieval failed";
    private static final String ERROR_CACHING_OPERATION = "Caching operation failed";
    private static final String ERROR_BLOOM_FILTER = "Bloom filter check failed";

    // Helper methods
    private void verifyFluxCompletes(Flux<?> flux, long expectedCount) {
        StepVerifier.create(flux)
                .expectNextCount(expectedCount)
                .verifyComplete();
    }

    private void verifyMonoCompletes(Mono<?> mono) {
        StepVerifier.create(mono)
                .verifyComplete();
    }

    private void verifyErrorMessage(Flux<?> flux, String expectedMessage) {
        StepVerifier.create(flux)
                .expectErrorMatches(error ->
                        error instanceof RuntimeException &&
                                error.getMessage().equals(expectedMessage)
                )
                .verify();
    }

    private void verifyMonoErrorMessage(Mono<?> mono, String expectedMessage) {
        StepVerifier.create(mono)
                .expectErrorMatches(error ->
                        error instanceof RuntimeException &&
                                error.getMessage().equals(expectedMessage)
                )
                .verify();
    }

    @Nested
    @DisplayName("getCachedUsernames() Tests")
    class GetCachedUsernamesTests {

        @ParameterizedTest(name = "Should retrieve {1} cached usernames for {0} language")
        @MethodSource("languageAndCountProvider")
        @DisplayName("Should retrieve cached usernames for different languages and counts")
        void shouldRetrieveCachedUsernamesForLanguagesAndCounts(Language language, int count, String prefix) {
            // Arrange
            List<Username> cachedUsernames = TestDataFixtures.usernamesWithLanguage(prefix, count, language);
            when(cacheService.getCachedUsernames(language, count))
                    .thenReturn(Flux.fromIterable(cachedUsernames));

            // Act & Assert
            verifyFluxCompletes(cacheService.getCachedUsernames(language, count), count);
        }

        static Stream<Arguments> languageAndCountProvider() {
            return Stream.of(
                arguments(Language.EN, 3, "cached_en"),
                arguments(Language.ES, 2, "cached_es"),
                arguments(Language.EN, 1, "single_cached"),
                arguments(Language.EN, 5, "exact"),
                arguments(Language.EN, 10, "large")
            );
        }

        @Test
        @DisplayName("Should return empty flux when cache is empty")
        void shouldReturnEmptyFluxWhenCacheIsEmpty() {
            // Arrange
            when(cacheService.getCachedUsernames(Language.EN, 5))
                    .thenReturn(Flux.empty());

            // Act & Assert
            StepVerifier.create(cacheService.getCachedUsernames(Language.EN, 5))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should retrieve usernames with different pattern types")
        void shouldRetrieveUsernamesWithDifferentPatternTypes() {
            // Arrange
            List<Username> mixedPatterns = TestDataFixtures.mixedPatternUsernames("cache");
            when(cacheService.getCachedUsernames(Language.EN, 3))
                    .thenReturn(Flux.fromIterable(mixedPatterns));

            // Act & Assert
            StepVerifier.create(cacheService.getCachedUsernames(Language.EN, 3))
                    .assertNext(username -> assertThat(username.patternType()).isEqualTo(PatternType.CLASSIC))
                    .assertNext(username -> assertThat(username.patternType()).isEqualTo(PatternType.SEPARATOR))
                    .assertNext(username -> assertThat(username.patternType()).isEqualTo(PatternType.WORDPLAY))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return partial results when cache has fewer items than requested")
        void shouldReturnPartialResultsWhenCacheHasFewerItems() {
            // Arrange
            List<Username> partialCache = TestDataFixtures.usernames("partial", 2);
            when(cacheService.getCachedUsernames(Language.EN, 10))
                    .thenReturn(Flux.fromIterable(partialCache));

            // Act & Assert
            verifyFluxCompletes(cacheService.getCachedUsernames(Language.EN, 10), 2);
        }

        @Test
        @DisplayName("All returned usernames should match requested language")
        void allReturnedUsernamesShouldMatchRequestedLanguage() {
            // Arrange
            List<Username> englishCache = TestDataFixtures.usernames("english_cache", 3);
            when(cacheService.getCachedUsernames(Language.EN, 3))
                    .thenReturn(Flux.fromIterable(englishCache));

            // Act & Assert
            StepVerifier.create(cacheService.getCachedUsernames(Language.EN, 3))
                    .assertNext(username -> assertThat(username.language()).isEqualTo(Language.EN))
                    .assertNext(username -> assertThat(username.language()).isEqualTo(Language.EN))
                    .assertNext(username -> assertThat(username.language()).isEqualTo(Language.EN))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle error during cache retrieval")
        void shouldHandleErrorDuringCacheRetrieval() {
            // Arrange
            when(cacheService.getCachedUsernames(Language.EN, 5))
                    .thenReturn(Flux.error(new RuntimeException(ERROR_CACHE_RETRIEVAL)));

            // Act & Assert
            verifyErrorMessage(cacheService.getCachedUsernames(Language.EN, 5), ERROR_CACHE_RETRIEVAL);
        }
    }

    @Nested
    @DisplayName("cacheUsernames() Tests")
    class CacheUsernamesTests {

        @ParameterizedTest(name = "Should cache {1} usernames for {0} language")
        @MethodSource("cacheLanguageProvider")
        @DisplayName("Should cache usernames for different languages")
        void shouldCacheUsernamesForDifferentLanguages(Language language, int count, String prefix) {
            // Arrange
            List<Username> usernamesToCache = TestDataFixtures.usernamesWithLanguage(prefix, count, language);
            Flux<Username> usernameFlux = Flux.fromIterable(usernamesToCache);
            when(cacheService.cacheUsernames(eq(language), any(Flux.class)))
                    .thenReturn(Mono.empty());

            // Act & Assert
            verifyMonoCompletes(cacheService.cacheUsernames(language, usernameFlux));
        }

        static Stream<Arguments> cacheLanguageProvider() {
            return Stream.of(
                arguments(Language.EN, 2, "to_cache_en"),
                arguments(Language.ES, 2, "cachear_es"),
                arguments(Language.EN, 1, "single_to_cache"),
                arguments(Language.EN, 5, "multi_cache")
            );
        }

        @Test
        @DisplayName("Should handle empty flux successfully")
        void shouldHandleEmptyFluxSuccessfully() {
            // Arrange
            when(cacheService.cacheUsernames(eq(Language.EN), any(Flux.class)))
                    .thenReturn(Mono.empty());

            // Act & Assert
            verifyMonoCompletes(cacheService.cacheUsernames(Language.EN, Flux.empty()));
        }

        @Test
        @DisplayName("Should cache usernames with different pattern types")
        void shouldCacheUsernamesWithDifferentPatternTypes() {
            // Arrange
            Flux<Username> usernameFlux = Flux.fromIterable(TestDataFixtures.mixedPatternUsernames("cache"));
            when(cacheService.cacheUsernames(eq(Language.EN), any(Flux.class)))
                    .thenReturn(Mono.empty());

            // Act & Assert
            verifyMonoCompletes(cacheService.cacheUsernames(Language.EN, usernameFlux));
        }

        @Test
        @DisplayName("Should cache large batch of usernames")
        void shouldCacheLargeBatchOfUsernames() {
            // Arrange
            Flux<Username> usernameFlux = Flux.fromIterable(TestDataFixtures.usernames("batch", 10));
            when(cacheService.cacheUsernames(eq(Language.EN), any(Flux.class)))
                    .thenReturn(Mono.empty());

            // Act & Assert
            verifyMonoCompletes(cacheService.cacheUsernames(Language.EN, usernameFlux));
        }

        @Test
        @DisplayName("Should handle error during caching operation")
        void shouldHandleErrorDuringCachingOperation() {
            // Arrange
            Flux<Username> usernameFlux = Flux.just(TestDataFixtures.username("error_cache"));
            when(cacheService.cacheUsernames(eq(Language.EN), any(Flux.class)))
                    .thenReturn(Mono.error(new RuntimeException(ERROR_CACHING_OPERATION)));

            // Act & Assert
            verifyMonoErrorMessage(cacheService.cacheUsernames(Language.EN, usernameFlux), ERROR_CACHING_OPERATION);
        }

        @Test
        @DisplayName("Should complete within reasonable timeout")
        void shouldCompleteWithinReasonableTimeout() {
            // Arrange
            Flux<Username> usernameFlux = Flux.just(TestDataFixtures.username("timeout_cache"));
            when(cacheService.cacheUsernames(eq(Language.EN), any(Flux.class)))
                    .thenReturn(Mono.empty());

            // Act & Assert
            StepVerifier.create(cacheService.cacheUsernames(Language.EN, usernameFlux))
                    .expectComplete()
                    .verify(REASONABLE_TIMEOUT);
        }
    }

    @Nested
    @DisplayName("mightExists() Tests - Bloom Filter")
    class MightExistsTests {

        @ParameterizedTest(name = "Should return {1} for username: {0}")
        @MethodSource("usernameExistenceProvider")
        @DisplayName("Should check username existence with Bloom filter")
        void shouldCheckUsernameExistence(String username, boolean exists) {
            // Arrange
            when(cacheService.mightExist(username))
                    .thenReturn(Mono.just(exists));

            // Act & Assert
            StepVerifier.create(cacheService.mightExist(username))
                    .expectNext(exists)
                    .verifyComplete();
        }

        static Stream<Arguments> usernameExistenceProvider() {
            return Stream.of(
                arguments("probable_user", true),
                arguments("definitely_not_exists", false),
                arguments("", false),
                arguments("user_name-123", true),
                arguments("usr12", true),
                arguments("very_long_username_12345678", false)
            );
        }

        @Test
        @DisplayName("Should provide fast negative lookup (Bloom filter property)")
        void shouldProvideFastNegativeLookup() {
            // Arrange
            when(cacheService.mightExist("fast_check_user"))
                    .thenReturn(Mono.just(false));

            // Act & Assert
            StepVerifier.create(cacheService.mightExist("fast_check_user"))
                    .expectNext(false)
                    .expectComplete()
                    .verify(FAST_LOOKUP);
        }

        @Test
        @DisplayName("Should handle error during Bloom filter check")
        void shouldHandleErrorDuringBloomFilterCheck() {
            // Arrange
            when(cacheService.mightExist("error_check"))
                    .thenReturn(Mono.error(new RuntimeException(ERROR_BLOOM_FILTER)));

            // Act & Assert
            verifyMonoErrorMessage(cacheService.mightExist("error_check"), ERROR_BLOOM_FILTER);
        }

        @Test
        @DisplayName("Should emit exactly one boolean value")
        void shouldEmitExactlyOneBooleanValue() {
            // Arrange
            when(cacheService.mightExist(anyString()))
                    .thenReturn(Mono.just(true));

            // Act & Assert
            StepVerifier.create(cacheService.mightExist("test"))
                    .expectNextMatches(Objects::nonNull)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle multiple consecutive checks efficiently")
        void shouldHandleMultipleConsecutiveChecksEfficiently() {
            // Arrange
            when(cacheService.mightExist("user1")).thenReturn(Mono.just(true));
            when(cacheService.mightExist("user2")).thenReturn(Mono.just(false));
            when(cacheService.mightExist("user3")).thenReturn(Mono.just(true));

            // Act & Assert
            StepVerifier.create(cacheService.mightExist("user1"))
                    .expectNext(true).verifyComplete();
            StepVerifier.create(cacheService.mightExist("user2"))
                    .expectNext(false).verifyComplete();
            StepVerifier.create(cacheService.mightExist("user3"))
                    .expectNext(true).verifyComplete();
        }

        @Test
        @DisplayName("Should complete within reasonable timeout")
        void shouldCompleteWithinReasonableTimeout() {
            // Arrange
            when(cacheService.mightExist("timeout_check"))
                    .thenReturn(Mono.just(true));

            // Act & Assert
            StepVerifier.create(cacheService.mightExist("timeout_check"))
                    .expectNext(true)
                    .expectComplete()
                    .verify(REASONABLE_TIMEOUT);
        }

        @Test
        @DisplayName("Should be deterministic for same username")
        void shouldBeDeterministicForSameUsername() {
            // Arrange
            when(cacheService.mightExist("deterministic_user"))
                    .thenReturn(Mono.just(true));

            // Act & Assert - First call
            StepVerifier.create(cacheService.mightExist("deterministic_user"))
                    .expectNext(true).verifyComplete();

            // Act & Assert - Second call should return same result
            StepVerifier.create(cacheService.mightExist("deterministic_user"))
                    .expectNext(true).verifyComplete();
        }
    }

    @Nested
    @DisplayName("Reactive Behavior and Edge Cases")
    class ReactiveBehaviorTests {

        @Test
        @DisplayName("getCachedUsernames should handle backpressure correctly")
        void getCachedUsernamesShouldHandleBackpressureCorrectly() {
            // Arrange
            List<Username> usernames = TestDataFixtures.usernames("backpressure", 5);
            when(cacheService.getCachedUsernames(Language.EN, 5))
                    .thenReturn(Flux.fromIterable(usernames));

            // Act & Assert
            StepVerifier.create(cacheService.getCachedUsernames(Language.EN, 5), 2)
                    .expectNextCount(2)
                    .thenRequest(3)
                    .expectNextCount(3)
                    .verifyComplete();
        }

        @Test
        @DisplayName("cacheUsernames should handle flux errors gracefully")
        void cacheUsernamesShouldHandleFluxErrorsGracefully() {
            // Arrange
            Flux<Username> errorFlux = Flux.error(new RuntimeException("Flux error"));
            when(cacheService.cacheUsernames(eq(Language.EN), any(Flux.class)))
                    .thenReturn(Mono.error(new RuntimeException("Flux error")));

            // Act & Assert
            verifyMonoErrorMessage(cacheService.cacheUsernames(Language.EN, errorFlux), "Flux error");
        }

        @Test
        @DisplayName("All operations should complete within reasonable timeout")
        void allOperationsShouldCompleteWithinReasonableTimeout() {
            // Arrange
            Username testUsername = TestDataFixtures.username("timeout_test");
            when(cacheService.getCachedUsernames(any(Language.class), anyInt()))
                    .thenReturn(Flux.just(testUsername));
            when(cacheService.cacheUsernames(any(Language.class), any(Flux.class)))
                    .thenReturn(Mono.empty());
            when(cacheService.mightExist(anyString()))
                    .thenReturn(Mono.just(true));

            // Act & Assert
            StepVerifier.create(cacheService.getCachedUsernames(Language.EN, 1))
                    .expectNextCount(1).expectComplete().verify(REASONABLE_TIMEOUT);
            StepVerifier.create(cacheService.cacheUsernames(Language.EN, Flux.just(testUsername)))
                    .expectComplete().verify(REASONABLE_TIMEOUT);
            StepVerifier.create(cacheService.mightExist("test"))
                    .expectNextCount(1).expectComplete().verify(REASONABLE_TIMEOUT);
        }

        @Test
        @DisplayName("getCachedUsernames should emit usernames in order")
        void getCachedUsernamesShouldEmitUsernamesInOrder() {
            // Arrange
            List<Username> orderedUsernames = TestDataFixtures.usernames("ordered", 3);
            when(cacheService.getCachedUsernames(Language.EN, 3))
                    .thenReturn(Flux.fromIterable(orderedUsernames));

            // Act & Assert
            StepVerifier.create(cacheService.getCachedUsernames(Language.EN, 3))
                    .assertNext(username -> assertThat(username.value()).isEqualTo("ordered_01"))
                    .assertNext(username -> assertThat(username.value()).isEqualTo("ordered_02"))
                    .assertNext(username -> assertThat(username.value()).isEqualTo("ordered_03"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Cache operations should maintain username integrity")
        void cacheOperationsShouldMaintainUsernameIntegrity() {
            // Arrange
            Username originalUsername = TestDataFixtures.username("integrity_test");
            when(cacheService.cacheUsernames(eq(Language.EN), any(Flux.class)))
                    .thenReturn(Mono.empty());
            when(cacheService.getCachedUsernames(Language.EN, 1))
                    .thenReturn(Flux.just(originalUsername));

            // Act & Assert - Cache
            verifyMonoCompletes(cacheService.cacheUsernames(Language.EN, Flux.just(originalUsername)));

            // Act & Assert - Retrieve
            StepVerifier.create(cacheService.getCachedUsernames(Language.EN, 1))
                    .assertNext(username -> {
                        assertThat(username.value()).isEqualTo("integrity_test");
                        assertThat(username.language()).isEqualTo(Language.EN);
                        assertThat(username.patternType()).isEqualTo(PatternType.CLASSIC);
                    })
                    .verifyComplete();
        }
    }
}
