package com.forge.adapters.outbound.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.domain.model.Language;
import com.forge.domain.model.Username;
import com.forge.domain.ports.outboung.CacheService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DisplayName("RedisCacheServiceAdapter Tests")
class RedisCacheServiceAdapterTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private CacheService cacheService;
    private ReactiveRedisTemplate<String, String> redisTemplate;
    private LettuceConnectionFactory connectionFactory;

    @BeforeEach
    void setUp() {
        // Create connection factory
        connectionFactory = new LettuceConnectionFactory(
                redis.getHost(),
                redis.getFirstMappedPort()
        );
        connectionFactory.afterPropertiesSet();

        // Create Redis template
        StringRedisSerializer serializer = new StringRedisSerializer();
        RedisSerializationContext<String, String> context = RedisSerializationContext
                .<String, String>newSerializationContext(serializer)
                .key(serializer)
                .value(serializer)
                .hashKey(serializer)
                .hashValue(serializer)
                .build();
        redisTemplate = new ReactiveRedisTemplate<>(connectionFactory, context);

        // Create cache service
        ObjectMapper objectMapper = new ObjectMapper();
        cacheService = new RedisCacheServiceAdapter(redisTemplate, objectMapper);

        // Clean Redis before each test
        redisTemplate.getConnectionFactory()
                .getReactiveConnection()
                .serverCommands()
                .flushAll()
                .block();
    }

    @AfterEach
    void tearDown() {
        // Cleanup
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    @DisplayName("Should Cache Usernames")
    void shouldCacheUsernames() {
        // ARRANGE
        Flux<Username> usernames = Flux.just(
                Username.of("cleverpanda42", Language.EN),
                Username.of("swifteagle99", Language.EN)
        );

        // ACT & ASSERT
        StepVerifier.create(cacheService.cacheUsernames(Language.EN, usernames))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Get Cached Usernames")
    void shouldGetCachedUsernames() {
        // ARRANGE
        Flux<Username> usernames = Flux.just(
                Username.of("user1", Language.EN),
                Username.of("user2", Language.EN)
        );
        cacheService.cacheUsernames(Language.EN, usernames).block();

        // ACT & ASSERT
        StepVerifier.create(cacheService.getCachedUsernames(Language.EN, 2))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Return Empty When Cache Is Empty")
    void shouldReturnEmptyWhenCacheIsEmpty() {
        // ACT & ASSERT
        StepVerifier.create(cacheService.getCachedUsernames(Language.EN, 5))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Return Requested Count Only")
    void shouldReturnRequestedCountOnly() {
        // ARRANGE
        Flux<Username> usernames = Flux.just(
                Username.of("user1", Language.EN),
                Username.of("user2", Language.EN),
                Username.of("user3", Language.EN),
                Username.of("user4", Language.EN),
                Username.of("user5", Language.EN)
        );
        cacheService.cacheUsernames(Language.EN, usernames).block();

        // ACT & ASSERT
        StepVerifier.create(cacheService.getCachedUsernames(Language.EN, 2))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Check If Username Might Exist")
    void shouldCheckIfUsernameMightExist() {
        // ARRANGE
        String username = "testuser42";

        // First time should not exist
        StepVerifier.create(cacheService.mightExist(username))
                .expectNext(false)
                .verifyComplete();

        // Add to Bloom filter simulation (simplified for test)
        Flux<Username> usernames = Flux.just(
                Username.of(username, Language.EN)
        );
        cacheService.cacheUsernames(Language.EN, usernames).block();

        // Now might exist
        StepVerifier.create(cacheService.mightExist(username))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Isolate Cache By Language")
    void shouldIsolateCacheByLanguage() {
        // ARRANGE
        Flux<Username> englishUsernames = Flux.just(
                Username.of("english1", Language.EN),
                Username.of("english2", Language.EN)
        );
        Flux<Username> spanishUsernames = Flux.just(
                Username.of("spanish1", Language.ES),
                Username.of("spanish2", Language.ES)
        );

        cacheService.cacheUsernames(Language.EN, englishUsernames).block();
        cacheService.cacheUsernames(Language.ES, spanishUsernames).block();

        // ACT & ASSERT - EN cache should only have EN usernames
        StepVerifier.create(cacheService.getCachedUsernames(Language.EN, 10))
                .expectNextCount(2)
                .verifyComplete();

        // ES cache should only have ES usernames
        StepVerifier.create(cacheService.getCachedUsernames(Language.ES, 10))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Handle Empty Flux Cache")
    void shouldHandleEmptyFluxCache() {
        // ARRANGE
        Flux<Username> emptyFlux = Flux.empty();

        // ACT & ASSERT
        StepVerifier.create(cacheService.cacheUsernames(Language.EN, emptyFlux))
                .verifyComplete();

        // Verify cache is still empty
        StepVerifier.create(cacheService.getCachedUsernames(Language.EN, 5))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Return Empty When Count Is Zero")
    void shouldReturnEmptyWhenCountIsZero() {
        // ARRANGE
        Flux<Username> usernames = Flux.just(
                Username.of("user1", Language.EN),
                Username.of("user2", Language.EN)
        );
        cacheService.cacheUsernames(Language.EN, usernames).block();

        // ACT & ASSERT
        StepVerifier.create(cacheService.getCachedUsernames(Language.EN, 0))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Preserve Username Properties When Cached")
    void shouldPreserveUsernamePropertiesWhenCached() {
        // ARRANGE
        Username original = Username.of("testuser123", Language.EN);
        Flux<Username> usernames = Flux.just(original);
        cacheService.cacheUsernames(Language.EN, usernames).block();

        // ACT & ASSERT
        StepVerifier.create(cacheService.getCachedUsernames(Language.EN, 1))
                .assertNext(cached -> {
                    assertThat(cached.value()).isEqualTo(original.value());
                    assertThat(cached.language()).isEqualTo(original.language());
                    assertThat(cached.patternType()).isEqualTo(original.patternType());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Append To Existing Cache")
    void shouldAppendToExistingCache() {
        // ARRANGE
        Flux<Username> firstBatch = Flux.just(
                Username.of("user1", Language.EN),
                Username.of("user2", Language.EN)
        );
        Flux<Username> secondBatch = Flux.just(
                Username.of("user3", Language.EN),
                Username.of("user4", Language.EN)
        );

        // ACT
        cacheService.cacheUsernames(Language.EN, firstBatch).block();
        cacheService.cacheUsernames(Language.EN, secondBatch).block();

        // ASSERT - Should have 4 total
        StepVerifier.create(cacheService.getCachedUsernames(Language.EN, 10))
                .expectNextCount(4)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Add All Usernames To Bloom Filter")
    void shouldAddAllUsernamesToBloomFilter() {
        // ARRANGE
        Flux<Username> usernames = Flux.just(
                Username.of("alpha123", Language.EN),
                Username.of("beta456", Language.EN),
                Username.of("gamma789", Language.EN)
        );
        cacheService.cacheUsernames(Language.EN, usernames).block();

        // ACT & ASSERT - All should be in bloom filter
        StepVerifier.create(cacheService.mightExist("alpha123"))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(cacheService.mightExist("beta456"))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(cacheService.mightExist("gamma789"))
                .expectNext(true)
                .verifyComplete();

        // Non-cached username should not exist
        StepVerifier.create(cacheService.mightExist("delta000"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Share Bloom Filter Across Languages")
    void shouldShareBloomFilterAcrossLanguages() {
        // ARRANGE - Cache usernames in different languages
        Flux<Username> englishUsernames = Flux.just(
                Username.of("english1", Language.EN)
        );
        Flux<Username> spanishUsernames = Flux.just(
                Username.of("spanish1", Language.ES)
        );

        cacheService.cacheUsernames(Language.EN, englishUsernames).block();
        cacheService.cacheUsernames(Language.ES, spanishUsernames).block();

        // ACT & ASSERT - Both should be in the shared bloom filter
        StepVerifier.create(cacheService.mightExist("english1"))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(cacheService.mightExist("spanish1"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Maintain FIFO Order In Cache")
    void shouldMaintainFifoOrderInCache() {
        // ARRANGE
        Flux<Username> usernames = Flux.just(
                Username.of("first", Language.EN),
                Username.of("second", Language.EN),
                Username.of("third", Language.EN)
        );
        cacheService.cacheUsernames(Language.EN, usernames).block();

        // ACT & ASSERT - Should return in same order
        StepVerifier.create(cacheService.getCachedUsernames(Language.EN, 3))
                .expectNext(Username.of("first", Language.EN))
                .expectNext(Username.of("second", Language.EN))
                .expectNext(Username.of("third", Language.EN))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Handle Large Batch Cache")
    void shouldHandleLargeBatchCache() {
        // ARRANGE - Create 100 usernames
        Flux<Username> largeFlux = Flux.range(1, 100)
                .map(i -> Username.of(String.format("user%03d", i), Language.EN));

        // ACT
        StepVerifier.create(cacheService.cacheUsernames(Language.EN, largeFlux))
                .verifyComplete();

        // ASSERT - Should retrieve all 100
        StepVerifier.create(cacheService.getCachedUsernames(Language.EN, 100))
                .expectNextCount(100)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should Return Correct Usernames When Languages Mixed In Bloom Filter")
    void shouldReturnCorrectUsernamesWhenLanguagesMixedInBloomFilter() {
        // ARRANGE - Cache different languages
        Flux<Username> englishUsernames = Flux.just(
                Username.of("english123", Language.EN),
                Username.of("english456", Language.EN)
        );
        Flux<Username> spanishUsernames = Flux.just(
                Username.of("spanish123", Language.ES),
                Username.of("spanish456", Language.ES)
        );

        cacheService.cacheUsernames(Language.EN, englishUsernames).block();
        cacheService.cacheUsernames(Language.ES, spanishUsernames).block();

        // ACT & ASSERT - Bloom filter has all, but cache is isolated
        StepVerifier.create(cacheService.mightExist("english123"))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(cacheService.mightExist("spanish123"))
                .expectNext(true)
                .verifyComplete();

        // But getCachedUsernames should only return the language requested
        StepVerifier.create(cacheService.getCachedUsernames(Language.EN, 10))
                .expectNextCount(2)
                .verifyComplete();
    }
}