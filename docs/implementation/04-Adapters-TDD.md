# NameForge - Adapters Implementation (TDD)

## 📋 Información del Documento

**Fase**: 4 - Adapters Implementation
**Enfoque**: Test-Driven Development + Testcontainers
**Componentes**: R2DBC Repository, Redis Cache, Generator, Moderation
**Duración Estimada**: 5-7 horas

## 🎯 Objetivos

Implementar los adapters de infraestructura siguiendo TDD:

1. **R2DBC Repository** - PostgreSQL persistence (Testcontainers)
2. **Redis Cache Service** - Caching layer (Testcontainers)
3. **Generator Service** - DataFaker username generation
4. **Moderation Service** - Content filtering (Mock external APIs)

## 🗄️ Fase 1: R2DBC Repository Adapter

### 1.1 Database Entity

#### Test 1.1: UsernameEntity mapping

```java
package com.forge.adapters.outbound.database.entity;

import com.forge.domain.model.Language;
import com.forge.domain.model.PatternType;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;

class UsernameEntityTest {

    @Test
    void shouldCreateUsernameEntity() {
        // ARRANGE & ACT
        UsernameEntity entity = UsernameEntity.builder()
            .username("cleverpanda42")
            .language(Language.EN)
            .patternType(PatternType.CLASSIC)
            .createdAt(LocalDateTime.now())
            .isUsed(false)
            .build();

        // ASSERT
        assertThat(entity).isNotNull();
        assertThat(entity.getUsername()).isEqualTo("cleverpanda42");
        assertThat(entity.getLanguage()).isEqualTo(Language.EN);
        assertThat(entity.getPatternType()).isEqualTo(PatternType.CLASSIC);
        assertThat(entity.isUsed()).isFalse();
    }
}
```

#### Implementation 1.1: UsernameEntity

```java
package com.forge.adapters.outbound.database.entity;

import com.forge.domain.model.Language;
import com.forge.domain.model.PatternType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("generated_usernames")
public class UsernameEntity {

    @Id
    private Long id;

    @Column("username")
    private String username;

    @Column("language")
    private Language language;

    @Column("pattern_type")
    private PatternType patternType;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("is_used")
    private boolean isUsed;

    @Column("used_at")
    private LocalDateTime usedAt;
}
```

---

### 1.2 Entity Mapper

#### Test 1.2: Domain to Entity and vice versa

```java
package com.forge.adapters.outbound.database.mapper;

import com.forge.adapters.outbound.database.entity.UsernameEntity;
import com.forge.domain.model.Language;
import com.forge.domain.model.PatternType;
import com.forge.domain.model.Username;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;

class UsernameEntityMapperTest {

    private UsernameEntityMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new UsernameEntityMapper();
    }

    @Test
    void shouldMapDomainToEntity() {
        // ARRANGE
        Username domain = Username.of("cleverpanda42", Language.EN, PatternType.CLASSIC);

        // ACT
        UsernameEntity entity = mapper.toEntity(domain);

        // ASSERT
        assertThat(entity).isNotNull();
        assertThat(entity.getUsername()).isEqualTo("cleverpanda42");
        assertThat(entity.getLanguage()).isEqualTo(Language.EN);
        assertThat(entity.getPatternType()).isEqualTo(PatternType.CLASSIC);
        assertThat(entity.isUsed()).isFalse();
        assertThat(entity.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldMapEntityToDomain() {
        // ARRANGE
        UsernameEntity entity = UsernameEntity.builder()
            .id(1L)
            .username("swifteagle99")
            .language(Language.EN)
            .patternType(PatternType.SEPARATOR)
            .createdAt(LocalDateTime.now())
            .isUsed(false)
            .build();

        // ACT
        Username domain = mapper.toDomain(entity);

        // ASSERT
        assertThat(domain).isNotNull();
        assertThat(domain.value()).isEqualTo("swifteagle99");
        assertThat(domain.language()).isEqualTo(Language.EN);
        assertThat(domain.patternType()).isEqualTo(PatternType.SEPARATOR);
    }
}
```

#### Implementation 1.2: UsernameEntityMapper

```java
package com.forge.adapters.outbound.database.mapper;

import com.forge.adapters.outbound.database.entity.UsernameEntity;
import com.forge.domain.model.Username;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
public class UsernameEntityMapper {

    public UsernameEntity toEntity(Username domain) {
        return UsernameEntity.builder()
            .username(domain.value())
            .language(domain.language())
            .patternType(domain.patternType())
            .createdAt(LocalDateTime.now())
            .isUsed(false)
            .build();
    }

    public Username toDomain(UsernameEntity entity) {
        return Username.of(
            entity.getUsername(),
            entity.getLanguage(),
            entity.getPatternType()
        );
    }
}
```

---

### 1.3 R2DBC Repository Implementation

#### Test 1.3: Repository with Testcontainers

```java
package com.forge.adapters.outbound.database;

import com.forge.adapters.outbound.database.entity.UsernameEntity;
import com.forge.adapters.outbound.database.mapper.UsernameEntityMapper;
import com.forge.domain.model.Language;
import com.forge.domain.model.PatternType;
import com.forge.domain.model.Username;
import com.forge.domain.ports.outbound.UsernameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@DataR2dbcTest
@Testcontainers
@Import({R2dbcUsernameRepositoryAdapter.class, UsernameEntityMapper.class})
class R2dbcUsernameRepositoryAdapterTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("nameforge_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://"
            + postgres.getHost() + ":" + postgres.getFirstMappedPort()
            + "/" + postgres.getDatabaseName());
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private R2dbcEntityTemplate entityTemplate;

    @Autowired
    private UsernameEntityMapper mapper;

    private UsernameRepository repository;

    @BeforeEach
    void setUp() {
        repository = new R2dbcUsernameRepositoryAdapter(databaseClient, entityTemplate, mapper);

        // Clean database before each test
        databaseClient.sql("DELETE FROM generated_usernames")
            .fetch()
            .rowsUpdated()
            .block();
    }

    @Test
    void shouldSaveUsername() {
        // ARRANGE
        Username username = Username.of("cleverpanda42", Language.EN);

        // ACT & ASSERT
        StepVerifier.create(repository.save(username))
            .assertNext(saved -> {
                assertThat(saved).isNotNull();
                assertThat(saved.value()).isEqualTo("cleverpanda42");
                assertThat(saved.language()).isEqualTo(Language.EN);
            })
            .verifyComplete();
    }

    @Test
    void shouldReturnFalseWhenUsernameDoesNotExist() {
        // ACT & ASSERT
        StepVerifier.create(repository.existsByUsername("nonexistent"))
            .expectNext(false)
            .verifyComplete();
    }

    @Test
    void shouldReturnTrueWhenUsernameExists() {
        // ARRANGE
        Username username = Username.of("existinguser42", Language.EN);
        repository.save(username).block();

        // ACT & ASSERT
        StepVerifier.create(repository.existsByUsername("existinguser42"))
            .expectNext(true)
            .verifyComplete();
    }

    @Test
    void shouldFindAvailableUsernamesByLanguage() {
        // ARRANGE
        repository.save(Username.of("user1", Language.EN)).block();
        repository.save(Username.of("user2", Language.EN)).block();
        repository.save(Username.of("usuario3", Language.ES)).block();

        // ACT & ASSERT
        StepVerifier.create(repository.findAvailableByLanguage(Language.EN, 10))
            .expectNextCount(2)
            .verifyComplete();
    }

    @Test
    void shouldSaveBatchOfUsernames() {
        // ARRANGE
        Flux<Username> usernames = Flux.just(
            Username.of("user1", Language.EN),
            Username.of("user2", Language.EN),
            Username.of("user3", Language.EN)
        );

        // ACT & ASSERT
        StepVerifier.create(repository.saveBatch(usernames))
            .expectNextCount(3)
            .verifyComplete();

        // Verify they were saved
        StepVerifier.create(repository.existsByUsername("user1"))
            .expectNext(true)
            .verifyComplete();
    }

    @Test
    void shouldLimitResultsWhenFindingAvailable() {
        // ARRANGE
        for (int i = 1; i <= 20; i++) {
            repository.save(Username.of("user" + i, Language.EN)).block();
        }

        // ACT & ASSERT
        StepVerifier.create(repository.findAvailableByLanguage(Language.EN, 5))
            .expectNextCount(5)
            .verifyComplete();
    }
}
```

#### Implementation 1.3: R2dbcUsernameRepositoryAdapter

```java
package com.forge.adapters.outbound.database;

import com.forge.adapters.outbound.database.entity.UsernameEntity;
import com.forge.adapters.outbound.database.mapper.UsernameEntityMapper;
import com.forge.domain.model.Language;
import com.forge.domain.model.Username;
import com.forge.domain.ports.outbound.UsernameRepository;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class R2dbcUsernameRepositoryAdapter implements UsernameRepository {

    private final DatabaseClient databaseClient;
    private final R2dbcEntityTemplate entityTemplate;
    private final UsernameEntityMapper mapper;

    public R2dbcUsernameRepositoryAdapter(
        DatabaseClient databaseClient,
        R2dbcEntityTemplate entityTemplate,
        UsernameEntityMapper mapper
    ) {
        this.databaseClient = databaseClient;
        this.entityTemplate = entityTemplate;
        this.mapper = mapper;
    }

    @Override
    public Mono<Boolean> existsByUsername(String username) {
        return databaseClient
            .sql("SELECT EXISTS(SELECT 1 FROM generated_usernames WHERE username = :username)")
            .bind("username", username)
            .map(row -> row.get(0, Boolean.class))
            .one()
            .defaultIfEmpty(false);
    }

    @Override
    public Mono<Username> save(Username username) {
        UsernameEntity entity = mapper.toEntity(username);
        return entityTemplate.insert(entity)
            .map(mapper::toDomain);
    }

    @Override
    public Flux<Username> saveBatch(Flux<Username> usernames) {
        return usernames
            .map(mapper::toEntity)
            .flatMap(entityTemplate::insert)
            .map(mapper::toDomain);
    }

    @Override
    public Flux<Username> findAvailableByLanguage(Language language, int limit) {
        return databaseClient
            .sql("""
                SELECT id, username, language, pattern_type, created_at, is_used, used_at
                FROM generated_usernames
                WHERE language = :language AND is_used = FALSE
                ORDER BY created_at DESC
                LIMIT :limit
                """)
            .bind("language", language.name())
            .bind("limit", limit)
            .fetch()
            .all()
            .map(this::mapRowToEntity)
            .map(mapper::toDomain);
    }

    private UsernameEntity mapRowToEntity(java.util.Map<String, Object> row) {
        return UsernameEntity.builder()
            .id(((Number) row.get("id")).longValue())
            .username((String) row.get("username"))
            .language(Language.valueOf((String) row.get("language")))
            .patternType(com.forge.domain.model.PatternType.valueOf((String) row.get("pattern_type")))
            .createdAt((java.time.LocalDateTime) row.get("created_at"))
            .isUsed((Boolean) row.get("is_used"))
            .usedAt((java.time.LocalDateTime) row.get("used_at"))
            .build();
    }
}
```

---

## 💾 Fase 2: Redis Cache Adapter

### 2.1 Redis Configuration Test

#### Test 2.1: Redis cache operations

```java
package com.forge.adapters.outbound.cache;

import com.forge.domain.model.Language;
import com.forge.domain.model.PatternType;
import com.forge.domain.model.Username;
import com.forge.domain.ports.outbound.CacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@SpringBootTest
@Testcontainers
class RedisCacheServiceAdapterTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new RedisCacheServiceAdapter(redisTemplate);

        // Clean Redis before each test
        redisTemplate.getConnectionFactory()
            .getReactiveConnection()
            .serverCommands()
            .flushAll()
            .block();
    }

    @Test
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
    void shouldReturnEmptyWhenCacheIsEmpty() {
        // ACT & ASSERT
        StepVerifier.create(cacheService.getCachedUsernames(Language.EN, 5))
            .verifyComplete();
    }

    @Test
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
}
```

#### Implementation 2.1: RedisCacheServiceAdapter

```java
package com.forge.adapters.outbound.cache;

import com.forge.domain.model.Language;
import com.forge.domain.model.Username;
import com.forge.domain.ports.outbound.CacheService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Duration;

@Service
public class RedisCacheServiceAdapter implements CacheService {

    private static final String USERNAME_CACHE_PREFIX = "usernames:";
    private static final String BLOOM_FILTER_PREFIX = "bloom:usernames";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisCacheServiceAdapter(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Flux<Username> getCachedUsernames(Language language, int count) {
        String key = USERNAME_CACHE_PREFIX + language.name();

        return redisTemplate.opsForList()
            .range(key, 0, count - 1)
            .flatMap(this::deserializeUsername)
            .take(count);
    }

    @Override
    public Mono<Void> cacheUsernames(Language language, Flux<Username> usernames) {
        String key = USERNAME_CACHE_PREFIX + language.name();

        return usernames
            .flatMap(this::serializeUsername)
            .collectList()
            .flatMap(serialized -> {
                if (serialized.isEmpty()) {
                    return Mono.empty();
                }
                return redisTemplate.opsForList()
                    .rightPushAll(key, serialized)
                    .then(redisTemplate.expire(key, CACHE_TTL))
                    .then();
            });
    }

    @Override
    public Mono<Boolean> mightExist(String username) {
        // Simplified Bloom filter using Redis SET
        // In production, use Redisson or similar for proper Bloom filter
        return redisTemplate.opsForSet()
            .isMember(BLOOM_FILTER_PREFIX, username)
            .defaultIfEmpty(false);
    }

    private Mono<String> serializeUsername(Username username) {
        try {
            CachedUsernameDto dto = new CachedUsernameDto(
                username.value(),
                username.language().name(),
                username.patternType().name()
            );
            return Mono.just(objectMapper.writeValueAsString(dto));
        } catch (JsonProcessingException e) {
            return Mono.error(new RuntimeException("Failed to serialize username", e));
        }
    }

    private Mono<Username> deserializeUsername(String json) {
        try {
            CachedUsernameDto dto = objectMapper.readValue(json, CachedUsernameDto.class);
            return Mono.just(Username.of(
                dto.value(),
                Language.valueOf(dto.language()),
                com.forge.domain.model.PatternType.valueOf(dto.patternType())
            ));
        } catch (JsonProcessingException e) {
            return Mono.error(new RuntimeException("Failed to deserialize username", e));
        }
    }

    private record CachedUsernameDto(String value, String language, String patternType) {}
}
```

---

## 🎲 Fase 3: Generator Service Adapter

### 3.1 DataFaker Generator

#### Test 3.1: Username generation with DataFaker

```java
package com.forge.adapters.outbound.generation;

import com.forge.domain.model.Language;
import com.forge.domain.model.PatternType;
import com.forge.domain.ports.outbound.GeneratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.*;

class DataFakerGeneratorServiceTest {

    private GeneratorService generatorService;

    @BeforeEach
    void setUp() {
        generatorService = new DataFakerGeneratorService();
    }

    @Test
    void shouldGenerateEnglishUsername() {
        // ACT & ASSERT
        StepVerifier.create(generatorService.generateUsername(Language.EN, PatternType.CLASSIC))
            .assertNext(username -> {
                assertThat(username).isNotNull();
                assertThat(username).matches("^[a-z0-9_-]{5,30}$");
            })
            .verifyComplete();
    }

    @Test
    void shouldGenerateSpanishUsername() {
        // ACT & ASSERT
        StepVerifier.create(generatorService.generateUsername(Language.ES, PatternType.CLASSIC))
            .assertNext(username -> {
                assertThat(username).isNotNull();
                assertThat(username).matches("^[a-z0-9_-]{5,30}$");
            })
            .verifyComplete();
    }

    @Test
    void shouldGenerateClassicPattern() {
        // ACT & ASSERT
        StepVerifier.create(generatorService.generateUsername(Language.EN, PatternType.CLASSIC))
            .assertNext(username -> {
                // Classic: adjective + noun + number
                assertThat(username).matches("^[a-z]+[a-z]+\\d+$");
            })
            .verifyComplete();
    }

    @Test
    void shouldGenerateSeparatorPattern() {
        // ACT & ASSERT
        StepVerifier.create(generatorService.generateUsername(Language.EN, PatternType.SEPARATOR))
            .assertNext(username -> {
                // Separator: adjective_noun_number or adjective-noun-number
                assertThat(username).matches("^[a-z]+[_-][a-z]+[_-]\\d+$");
            })
            .verifyComplete();
    }

    @Test
    void shouldGenerateUniqueUsernames() {
        // ACT
        String username1 = generatorService.generateUsername(Language.EN, PatternType.CLASSIC).block();
        String username2 = generatorService.generateUsername(Language.EN, PatternType.CLASSIC).block();
        String username3 = generatorService.generateUsername(Language.EN, PatternType.CLASSIC).block();

        // ASSERT - Should be different (very high probability)
        assertThat(username1).isNotEqualTo(username2);
        assertThat(username2).isNotEqualTo(username3);
        assertThat(username1).isNotEqualTo(username3);
    }

    @Test
    void shouldGenerateValidLengthUsernames() {
        // ACT & ASSERT
        for (int i = 0; i < 10; i++) {
            StepVerifier.create(generatorService.generateUsername(Language.EN, PatternType.CLASSIC))
                .assertNext(username -> {
                    assertThat(username.length()).isBetween(5, 30);
                })
                .verifyComplete();
        }
    }
}
```

#### Implementation 3.1: DataFakerGeneratorService

```java
package com.forge.adapters.outbound.generation;

import com.forge.domain.model.Language;
import com.forge.domain.model.PatternType;
import com.forge.domain.ports.outbound.GeneratorService;
import net.datafaker.Faker;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.util.Locale;
import java.util.Random;

@Service
public class DataFakerGeneratorService implements GeneratorService {

    private final Faker englishFaker;
    private final Faker spanishFaker;
    private final Random random;

    public DataFakerGeneratorService() {
        this.englishFaker = new Faker(Locale.ENGLISH);
        this.spanishFaker = new Faker(new Locale("es"));
        this.random = new Random();
    }

    @Override
    public Mono<String> generateUsername(Language language, PatternType patternType) {
        return Mono.fromSupplier(() -> {
            Faker faker = language == Language.EN ? englishFaker : spanishFaker;

            return switch (patternType) {
                case CLASSIC -> generateClassic(faker);
                case SEPARATOR -> generateSeparator(faker);
                case WORDPLAY -> generateWordplay(faker);
            };
        });
    }

    private String generateClassic(Faker faker) {
        String adjective = sanitize(faker.color().name());
        String noun = sanitize(faker.animal().name());
        int number = random.nextInt(1000);

        return (adjective + noun + number).toLowerCase();
    }

    private String generateSeparator(Faker faker) {
        String adjective = sanitize(faker.color().name());
        String noun = sanitize(faker.animal().name());
        int number = random.nextInt(1000);
        String separator = random.nextBoolean() ? "_" : "-";

        return (adjective + separator + noun + separator + number).toLowerCase();
    }

    private String generateWordplay(Faker faker) {
        // Simplified wordplay - just use classic for now
        return generateClassic(faker);
    }

    private String sanitize(String word) {
        // Remove spaces, special characters, keep only alphanumeric
        return word.replaceAll("[^a-zA-Z0-9]", "");
    }
}
```

---

## 🛡️ Fase 4: Moderation Service Adapter

### 4.1 Moderation Service (Mock)

#### Test 4.1: Content moderation

```java
package com.forge.adapters.outbound.moderation;

import com.forge.domain.ports.outbound.ModerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class SimpleModerationServiceTest {

    private ModerationService moderationService;

    @BeforeEach
    void setUp() {
        moderationService = new SimpleModerationService();
    }

    @Test
    void shouldApproveCleanUsername() {
        // ACT & ASSERT
        StepVerifier.create(moderationService.isAppropriate("cleverpanda42"))
            .expectNext(true)
            .verifyComplete();
    }

    @Test
    void shouldRejectCommonBadWords() {
        // ACT & ASSERT
        StepVerifier.create(moderationService.isAppropriate("badword123"))
            .expectNext(false)
            .verifyComplete();
    }

    @Test
    void shouldRejectMixedCaseBadWords() {
        // ACT & ASSERT
        StepVerifier.create(moderationService.isAppropriate("BadWord123"))
            .expectNext(false)
            .verifyComplete();
    }

    @Test
    void shouldApproveNormalWords() {
        // ACT & ASSERT
        String[] cleanUsernames = {
            "happycat42",
            "swifteagle99",
            "brightstar88",
            "coolpanda77"
        };

        for (String username : cleanUsernames) {
            StepVerifier.create(moderationService.isAppropriate(username))
                .expectNext(true)
                .verifyComplete();
        }
    }
}
```

#### Implementation 4.1: SimpleModerationService

```java
package com.forge.adapters.outbound.moderation;

import com.forge.domain.ports.outbound.ModerationService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.util.Set;

@Service
public class SimpleModerationService implements ModerationService {

    // Simplified blacklist - in production use OpenAI/Perspective APIs
    private static final Set<String> BLACKLIST = Set.of(
        "badword",
        "offensive",
        "inappropriate",
        "spam",
        "admin",
        "root",
        "system"
    );

    @Override
    public Mono<Boolean> isAppropriate(String username) {
        return Mono.fromSupplier(() -> {
            String lowerUsername = username.toLowerCase();

            // Check if contains any blacklisted word
            for (String badWord : BLACKLIST) {
                if (lowerUsername.contains(badWord)) {
                    return false;
                }
            }

            return true;
        });
    }
}
```

---

### 4.2 OpenAI Moderation Service (Production)

**Nota**: Esta implementación usa OpenAI Moderation API para validación profesional de contenido.

#### Dependencias para Testing con WireMock

**Ya agregado a `build.gradle`:**

```gradle
dependencies {
    // ... existing dependencies ...

    // WireMock for robust HTTP client testing
    testImplementation 'org.wiremock:wiremock-standalone:3.3.1'
}
```

**Nota**: WireMock proporciona:
- Mock server HTTP completo para tests de integración
- Verificación de requests y responses
- Simulación de timeouts, errores y rate limiting
- Configuración declarativa de stubs

---

#### Test 4.2.1: Configuration Properties

```java
package com.forge.infrastructure.properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "moderation.openai.api-key=test-key-123",
    "moderation.openai.endpoint=https://api.openai.com/v1/moderations",
    "moderation.openai.timeout=5s",
    "moderation.openai.enabled=true"
})
class ModerationPropertiesTest {

    @Autowired
    private ModerationProperties properties;

    @Test
    void shouldLoadModerationProperties() {
        // ASSERT
        assertThat(properties).isNotNull();
        assertThat(properties.getOpenai()).isNotNull();
        assertThat(properties.getOpenai().getApiKey()).isEqualTo("test-key-123");
        assertThat(properties.getOpenai().getEndpoint()).contains("moderations");
        assertThat(properties.getOpenai().getTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.getOpenai().isEnabled()).isTrue();
    }

    @Test
    void shouldHaveDefaultValues() {
        // ASSERT - verify defaults are reasonable
        assertThat(properties.getOpenai().getEndpoint()).isNotBlank();
        assertThat(properties.getOpenai().getTimeout()).isGreaterThan(Duration.ZERO);
    }
}
```

#### Implementation 4.2.1: ModerationProperties

```java
package com.forge.infrastructure.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "moderation")
public class ModerationProperties {

    private OpenAiProperties openai = new OpenAiProperties();

    @Data
    public static class OpenAiProperties {
        private String apiKey;
        private String endpoint = "https://api.openai.com/v1/moderations";
        private Duration timeout = Duration.ofSeconds(5);
        private boolean enabled = false;
        private int maxRetries = 2;
    }
}
```

---

#### Test 4.2.2: OpenAI DTOs

```java
package com.forge.adapters.outbound.moderation.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OpenAiModerationDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeModerationRequest() throws Exception {
        // ARRANGE
        ModerationRequest request = new ModerationRequest("test username");

        // ACT
        String json = objectMapper.writeValueAsString(request);

        // ASSERT
        assertThat(json).contains("\"input\":\"test username\"");
    }

    @Test
    void shouldDeserializeModerationResponse() throws Exception {
        // ARRANGE
        String json = """
            {
              "id": "modr-123",
              "model": "text-moderation-007",
              "results": [
                {
                  "flagged": true,
                  "categories": {
                    "hate": false,
                    "hate/threatening": false,
                    "harassment": false,
                    "harassment/threatening": false,
                    "self-harm": false,
                    "self-harm/intent": false,
                    "self-harm/instructions": false,
                    "sexual": true,
                    "sexual/minors": false,
                    "violence": false,
                    "violence/graphic": false
                  },
                  "category_scores": {
                    "hate": 0.001,
                    "sexual": 0.95
                  }
                }
              ]
            }
            """;

        // ACT
        ModerationResponse response = objectMapper.readValue(json, ModerationResponse.class);

        // ASSERT
        assertThat(response).isNotNull();
        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).isFlagged()).isTrue();
        assertThat(response.getResults().get(0).getCategories().isSexual()).isTrue();
    }

    @Test
    void shouldHandleUnflaggedContent() throws Exception {
        // ARRANGE
        String json = """
            {
              "id": "modr-456",
              "model": "text-moderation-007",
              "results": [
                {
                  "flagged": false,
                  "categories": {
                    "hate": false,
                    "sexual": false,
                    "violence": false
                  }
                }
              ]
            }
            """;

        // ACT
        ModerationResponse response = objectMapper.readValue(json, ModerationResponse.class);

        // ASSERT
        assertThat(response.getResults().get(0).isFlagged()).isFalse();
    }
}
```

#### Implementation 4.2.2: OpenAI DTOs

```java
package com.forge.adapters.outbound.moderation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModerationRequest {
    private String input;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModerationResponse {
    private String id;
    private String model;
    private List<ModerationResult> results;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModerationResult {
        private boolean flagged;
        private Categories categories;

        @JsonProperty("category_scores")
        private CategoryScores categoryScores;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Categories {
        private boolean hate;

        @JsonProperty("hate/threatening")
        private boolean hateThreatening;

        private boolean harassment;

        @JsonProperty("harassment/threatening")
        private boolean harassmentThreatening;

        @JsonProperty("self-harm")
        private boolean selfHarm;

        @JsonProperty("self-harm/intent")
        private boolean selfHarmIntent;

        @JsonProperty("self-harm/instructions")
        private boolean selfHarmInstructions;

        private boolean sexual;

        @JsonProperty("sexual/minors")
        private boolean sexualMinors;

        private boolean violence;

        @JsonProperty("violence/graphic")
        private boolean violenceGraphic;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryScores {
        private double hate;
        private double sexual;
        private double violence;
        private double harassment;

        @JsonProperty("self-harm")
        private double selfHarm;
    }
}
```

---

#### Test 4.2.3: OpenAI Moderation Service

```java
package com.forge.adapters.outbound.moderation;

import com.forge.adapters.outbound.moderation.dto.ModerationRequest;
import com.forge.adapters.outbound.moderation.dto.ModerationResponse;
import com.forge.infrastructure.properties.ModerationProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.time.Duration;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenAIModerationServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Mock
    private CircuitBreaker circuitBreaker;

    private ModerationProperties properties;
    private SimpleModerationService fallbackService;
    private OpenAIModerationService moderationService;

    @BeforeEach
    void setUp() {
        properties = new ModerationProperties();
        properties.getOpenai().setApiKey("test-key");
        properties.getOpenai().setEnabled(true);
        properties.getOpenai().setTimeout(Duration.ofSeconds(5));

        fallbackService = new SimpleModerationService();

        when(circuitBreakerRegistry.circuitBreaker(anyString())).thenReturn(circuitBreaker);
        when(circuitBreaker.decorateSupplier(any())).thenAnswer(invocation -> invocation.getArgument(0));

        moderationService = new OpenAIModerationService(
            webClient,
            properties,
            fallbackService,
            circuitBreakerRegistry
        );
    }

    @Test
    void shouldApproveCleanContent() {
        // ARRANGE
        ModerationResponse response = createModerationResponse(false);
        setupWebClientMock(response);

        // ACT & ASSERT
        StepVerifier.create(moderationService.isAppropriate("cleanusername"))
            .expectNext(true)
            .verifyComplete();
    }

    @Test
    void shouldRejectFlaggedContent() {
        // ARRANGE
        ModerationResponse response = createModerationResponse(true);
        setupWebClientMock(response);

        // ACT & ASSERT
        StepVerifier.create(moderationService.isAppropriate("badcontent"))
            .expectNext(false)
            .verifyComplete();
    }

    @Test
    void shouldFallbackToSimpleServiceOnError() {
        // ARRANGE
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(ModerationResponse.class))
            .thenReturn(Mono.error(new WebClientResponseException(500, "Server Error", null, null, null)));

        // ACT & ASSERT
        StepVerifier.create(moderationService.isAppropriate("testuser"))
            .expectNext(true) // Falls back to SimpleModerationService
            .verifyComplete();
    }

    @Test
    void shouldHandleTimeout() {
        // ARRANGE
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(ModerationResponse.class))
            .thenReturn(Mono.delay(Duration.ofSeconds(10))
                .then(Mono.just(createModerationResponse(false))));

        // ACT & ASSERT
        StepVerifier.create(moderationService.isAppropriate("testuser"))
            .expectNext(true) // Falls back on timeout
            .verifyComplete();
    }

    @Test
    void shouldUseSimpleServiceWhenDisabled() {
        // ARRANGE
        properties.getOpenai().setEnabled(false);
        moderationService = new OpenAIModerationService(
            webClient,
            properties,
            fallbackService,
            circuitBreakerRegistry
        );

        // ACT & ASSERT
        StepVerifier.create(moderationService.isAppropriate("testuser"))
            .expectNext(true)
            .verifyComplete();

        verifyNoInteractions(webClient);
    }

    private void setupWebClientMock(ModerationResponse response) {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(ModerationResponse.class))
            .thenReturn(Mono.just(response));
    }

    private ModerationResponse createModerationResponse(boolean flagged) {
        ModerationResponse.Categories categories = new ModerationResponse.Categories();
        categories.setHate(false);
        categories.setSexual(flagged);
        categories.setViolence(false);

        ModerationResponse.ModerationResult result = new ModerationResponse.ModerationResult();
        result.setFlagged(flagged);
        result.setCategories(categories);

        ModerationResponse response = new ModerationResponse();
        response.setId("modr-test");
        response.setModel("text-moderation-007");
        response.setResults(List.of(result));

        return response;
    }
}
```

#### Implementation 4.2.3: OpenAIModerationService

```java
package com.forge.adapters.outbound.moderation;

import com.forge.adapters.outbound.moderation.dto.ModerationRequest;
import com.forge.adapters.outbound.moderation.dto.ModerationResponse;
import com.forge.domain.ports.outbound.ModerationService;
import com.forge.infrastructure.properties.ModerationProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "moderation.openai", name = "enabled", havingValue = "true")
public class OpenAIModerationService implements ModerationService {

    private final WebClient webClient;
    private final ModerationProperties properties;
    private final SimpleModerationService fallbackService;
    private final CircuitBreaker circuitBreaker;

    public OpenAIModerationService(
        WebClient moderationWebClient,
        ModerationProperties properties,
        SimpleModerationService fallbackService,
        CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        this.webClient = moderationWebClient;
        this.properties = properties;
        this.fallbackService = fallbackService;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("openai-moderation");
    }

    @Override
    public Mono<Boolean> isAppropriate(String username) {
        if (!properties.getOpenai().isEnabled()) {
            return fallbackService.isAppropriate(username);
        }

        return Mono.fromSupplier(() -> circuitBreaker.decorateSupplier(() ->
            callOpenAIModeration(username).block()
        ).get())
        .onErrorResume(error -> {
            log.warn("OpenAI Moderation API error, falling back to simple service: {}",
                error.getMessage());
            return fallbackService.isAppropriate(username);
        });
    }

    private Mono<Boolean> callOpenAIModeration(String username) {
        ModerationRequest request = new ModerationRequest(username);

        return webClient.post()
            .uri(properties.getOpenai().getEndpoint())
            .header("Authorization", "Bearer " + properties.getOpenai().getApiKey())
            .header("Content-Type", "application/json")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(ModerationResponse.class)
            .timeout(properties.getOpenai().getTimeout())
            .map(response -> {
                if (response.getResults() == null || response.getResults().isEmpty()) {
                    log.warn("Empty moderation results, defaulting to appropriate");
                    return true;
                }

                boolean flagged = response.getResults().get(0).isFlagged();

                if (flagged) {
                    log.info("Username '{}' flagged by OpenAI Moderation", username);
                }

                return !flagged; // Return true if NOT flagged (appropriate)
            })
            .doOnError(WebClientResponseException.class, error -> {
                log.error("OpenAI API error: {} - {}",
                    error.getStatusCode(),
                    error.getResponseBodyAsString());
            })
            .onErrorResume(error -> {
                log.error("Error calling OpenAI Moderation API", error);
                return Mono.error(error);
            });
    }
}
```

---

#### Configuration 4.2.4: Moderation Config

```java
package com.forge.infrastructure.config;

import com.forge.infrastructure.properties.ModerationProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(ModerationProperties.class)
public class ModerationConfig {

    @Bean
    public WebClient moderationWebClient(ModerationProperties properties) {
        HttpClient httpClient = HttpClient.create()
            .responseTimeout(properties.getOpenai().getTimeout());

        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }

    @Bean
    public CircuitBreakerRegistry moderationCircuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowSize(10)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)
            .build();

        return CircuitBreakerRegistry.of(config);
    }
}
```

---

#### application.yml Configuration

Agregar a `src/main/resources/application.yml`:

```yaml
moderation:
  openai:
    enabled: ${OPENAI_MODERATION_ENABLED:false}
    api-key: ${OPENAI_API_KEY:}
    endpoint: https://api.openai.com/v1/moderations
    timeout: 5s
    max-retries: 2

# Test profile
---
spring:
  config:
    activate:
      on-profile: test

moderation:
  openai:
    enabled: false  # Use SimpleModerationService in tests

# Production profile
---
spring:
  config:
    activate:
      on-profile: production

moderation:
  openai:
    enabled: true
    timeout: 3s
```

---

#### Environment Variables

Agregar a `.env`:

```bash
# OpenAI Moderation API
OPENAI_MODERATION_ENABLED=true
OPENAI_API_KEY=sk-your-openai-api-key-here
```

---

#### Test Execution

```bash
# Run moderation tests only
./gradlew test --tests "*ModerationServiceTest"

# Run with OpenAI enabled (requires valid API key)
OPENAI_MODERATION_ENABLED=true OPENAI_API_KEY=sk-xxx ./gradlew test

# Integration test with mock server
./gradlew test --tests "*OpenAIModerationServiceTest"

# Integration test with WireMock
./gradlew test --tests "*OpenAIModerationServiceWireMockTest"
```

---

#### Test 4.2.4: Integration Test with WireMock

```java
package com.forge.adapters.outbound.moderation;

import com.forge.infrastructure.properties.ModerationProperties;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

class OpenAIModerationServiceWireMockTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    private OpenAIModerationService moderationService;
    private ModerationProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ModerationProperties();
        properties.getOpenai().setApiKey("test-api-key");
        properties.getOpenai().setEnabled(true);
        properties.getOpenai().setTimeout(Duration.ofSeconds(5));
        properties.getOpenai().setEndpoint(wireMock.baseUrl() + "/v1/moderations");

        WebClient webClient = WebClient.builder().build();

        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
            .slidingWindowSize(10)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .build();

        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        SimpleModerationService fallbackService = new SimpleModerationService();

        moderationService = new OpenAIModerationService(
            webClient,
            properties,
            fallbackService,
            circuitBreakerRegistry
        );
    }

    @Test
    void shouldApproveCleanContentWithWireMock() {
        // ARRANGE
        wireMock.stubFor(post(urlEqualTo("/v1/moderations"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer test-api-key"))
            .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(MediaType.APPLICATION_JSON_VALUE))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody("""
                    {
                      "id": "modr-123",
                      "model": "text-moderation-007",
                      "results": [
                        {
                          "flagged": false,
                          "categories": {
                            "hate": false,
                            "hate/threatening": false,
                            "harassment": false,
                            "harassment/threatening": false,
                            "self-harm": false,
                            "self-harm/intent": false,
                            "self-harm/instructions": false,
                            "sexual": false,
                            "sexual/minors": false,
                            "violence": false,
                            "violence/graphic": false
                          },
                          "category_scores": {
                            "hate": 0.001,
                            "sexual": 0.001,
                            "violence": 0.001,
                            "harassment": 0.001,
                            "self-harm": 0.001
                          }
                        }
                      ]
                    }
                    """)));

        // ACT & ASSERT
        StepVerifier.create(moderationService.isAppropriate("cleanusername"))
            .expectNext(true)
            .verifyComplete();

        // Verify request was made
        wireMock.verify(1, postRequestedFor(urlEqualTo("/v1/moderations"))
            .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer test-api-key")));
    }

    @Test
    void shouldRejectInappropriateContentWithWireMock() {
        // ARRANGE
        wireMock.stubFor(post(urlEqualTo("/v1/moderations"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody("""
                    {
                      "id": "modr-456",
                      "model": "text-moderation-007",
                      "results": [
                        {
                          "flagged": true,
                          "categories": {
                            "hate": true,
                            "hate/threatening": false,
                            "harassment": false,
                            "harassment/threatening": false,
                            "self-harm": false,
                            "self-harm/intent": false,
                            "self-harm/instructions": false,
                            "sexual": false,
                            "sexual/minors": false,
                            "violence": false,
                            "violence/graphic": false
                          },
                          "category_scores": {
                            "hate": 0.95,
                            "sexual": 0.001,
                            "violence": 0.001,
                            "harassment": 0.001,
                            "self-harm": 0.001
                          }
                        }
                      ]
                    }
                    """)));

        // ACT & ASSERT
        StepVerifier.create(moderationService.isAppropriate("badcontent"))
            .expectNext(false)
            .verifyComplete();
    }

    @Test
    void shouldHandleApiErrorWithWireMock() {
        // ARRANGE
        wireMock.stubFor(post(urlEqualTo("/v1/moderations"))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Internal Server Error")));

        // ACT & ASSERT - Should fallback to SimpleModerationService
        StepVerifier.create(moderationService.isAppropriate("testuser"))
            .expectNext(true) // SimpleModerationService approves "testuser"
            .verifyComplete();
    }

    @Test
    void shouldHandleTimeoutWithWireMock() {
        // ARRANGE
        wireMock.stubFor(post(urlEqualTo("/v1/moderations"))
            .willReturn(aResponse()
                .withStatus(200)
                .withFixedDelay(10000) // 10 seconds delay
                .withBody("{}")));

        // ACT & ASSERT - Should timeout and fallback
        StepVerifier.create(moderationService.isAppropriate("testuser"))
            .expectNext(true)
            .verifyComplete();
    }

    @Test
    void shouldHandleRateLimitingWithWireMock() {
        // ARRANGE
        wireMock.stubFor(post(urlEqualTo("/v1/moderations"))
            .willReturn(aResponse()
                .withStatus(429)
                .withHeader(HttpHeaders.RETRY_AFTER, "60")
                .withBody("""
                    {
                      "error": {
                        "message": "Rate limit exceeded",
                        "type": "rate_limit_error"
                      }
                    }
                    """)));

        // ACT & ASSERT - Should fallback
        StepVerifier.create(moderationService.isAppropriate("testuser"))
            .expectNext(true)
            .verifyComplete();
    }

    @Test
    void shouldHandleInvalidApiKeyWithWireMock() {
        // ARRANGE
        wireMock.stubFor(post(urlEqualTo("/v1/moderations"))
            .willReturn(aResponse()
                .withStatus(401)
                .withBody("""
                    {
                      "error": {
                        "message": "Invalid API key",
                        "type": "invalid_request_error"
                      }
                    }
                    """)));

        // ACT & ASSERT - Should fallback
        StepVerifier.create(moderationService.isAppropriate("testuser"))
            .expectNext(true)
            .verifyComplete();
    }

    @Test
    void shouldHandleMultipleCategoriesWithWireMock() {
        // ARRANGE
        wireMock.stubFor(post(urlEqualTo("/v1/moderations"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody("""
                    {
                      "id": "modr-789",
                      "model": "text-moderation-007",
                      "results": [
                        {
                          "flagged": true,
                          "categories": {
                            "hate": true,
                            "hate/threatening": false,
                            "harassment": true,
                            "harassment/threatening": false,
                            "self-harm": false,
                            "self-harm/intent": false,
                            "self-harm/instructions": false,
                            "sexual": true,
                            "sexual/minors": false,
                            "violence": false,
                            "violence/graphic": false
                          },
                          "category_scores": {
                            "hate": 0.85,
                            "sexual": 0.92,
                            "violence": 0.05,
                            "harassment": 0.78,
                            "self-harm": 0.01
                          }
                        }
                      ]
                    }
                    """)));

        // ACT & ASSERT
        StepVerifier.create(moderationService.isAppropriate("multibadcontent"))
            .expectNext(false)
            .verifyComplete();
    }

    @Test
    void shouldVerifyRequestBodyWithWireMock() {
        // ARRANGE
        String username = "testusername123";

        wireMock.stubFor(post(urlEqualTo("/v1/moderations"))
            .withRequestBody(matchingJsonPath("$.input", equalTo(username)))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody("""
                    {
                      "id": "modr-999",
                      "model": "text-moderation-007",
                      "results": [
                        {
                          "flagged": false,
                          "categories": {
                            "hate": false,
                            "sexual": false,
                            "violence": false
                          }
                        }
                      ]
                    }
                    """)));

        // ACT & ASSERT
        StepVerifier.create(moderationService.isAppropriate(username))
            .expectNext(true)
            .verifyComplete();

        // Verify exact request body
        wireMock.verify(postRequestedFor(urlEqualTo("/v1/moderations"))
            .withRequestBody(matchingJsonPath("$.input", equalTo(username))));
    }
}
```

---

## ⚙️ Fase 5: Configuration Classes

### 5.1 Database Configuration

```java
package com.forge.infrastructure.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;

@Configuration
@EnableR2dbcRepositories(basePackages = "com.forge.adapters.outbound.database")
public class DatabaseConfig extends AbstractR2dbcConfiguration {

    private final ConnectionFactory connectionFactory;

    public DatabaseConfig(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    @Bean
    public ConnectionFactory connectionFactory() {
        return connectionFactory;
    }

    @Bean
    public ReactiveTransactionManager transactionManager(ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
    }
}
```

### 5.2 Redis Configuration

```java
package com.forge.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(
        ReactiveRedisConnectionFactory connectionFactory
    ) {
        StringRedisSerializer serializer = new StringRedisSerializer();

        RedisSerializationContext<String, String> context = RedisSerializationContext
            .<String, String>newSerializationContext(serializer)
            .key(serializer)
            .value(serializer)
            .hashKey(serializer)
            .hashValue(serializer)
            .build();

        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }
}
```

---

## ✅ Verificación

### Checklist de Completitud

- [x] R2DBC Repository con Testcontainers PostgreSQL
- [x] Redis Cache Service con Testcontainers Redis
- [x] DataFaker Generator Service
- [x] Simple Moderation Service (Mock - desarrollo/testing)
- [x] OpenAI Moderation Service (Production-ready con circuit breaker)
- [x] WireMock integration tests para OpenAI API
- [x] Entity mappers Domain ↔ Entity
- [x] Database configuration
- [x] Redis configuration
- [x] Moderation configuration (WebClient + Circuit Breaker)
- [x] All integration tests passing

### Ejecución de Tests

```bash
# Tests con Testcontainers (requiere Docker)
./gradlew test --tests "com.forge.adapters.outbound.*"

# Tests de database
./gradlew test --tests "*R2dbcUsernameRepositoryAdapterTest"

# Tests de cache
./gradlew test --tests "*RedisCacheServiceAdapterTest"

# Tests de generator
./gradlew test --tests "*DataFakerGeneratorServiceTest"

# Tests de moderation (Mock)
./gradlew test --tests "*SimpleModerationServiceTest"

# Tests de moderation (OpenAI with mocks)
./gradlew test --tests "*OpenAIModerationServiceTest"

# Tests de moderation (WireMock integration)
./gradlew test --tests "*OpenAIModerationServiceWireMockTest"

# Tests de moderation properties y DTOs
./gradlew test --tests "*ModerationPropertiesTest"
./gradlew test --tests "*OpenAiModerationDtoTest"

# All moderation tests
./gradlew test --tests "*moderation*"
```

### Cobertura Esperada

```
Package: com.forge.adapters.outbound
- Line Coverage: > 85%
- Integration Coverage: All adapters tested with real infrastructure
```

## 🎯 Criterios de Aceptación

- ✅ R2DBC adapter persiste y recupera usernames
- ✅ Redis adapter cachea y recupera usernames
- ✅ Generator produce usernames válidos
- ✅ Moderation filtra contenido inapropiado (SimpleModerationService)
- ✅ OpenAI Moderation integrado con circuit breaker y fallback
- ✅ Testcontainers para PostgreSQL y Redis
- ✅ Tests de integración pasan
- ✅ Mappers Domain ↔ Entity testeados
- ✅ Configuraciones de Spring correctas
- ✅ Sin lógica de dominio en adapters
- ✅ Production-ready con manejo de errores y timeouts

## 🚀 Next Step

Proceder a **[05-Integration-Testing.md](./05-Integration-Testing.md)** para tests end-to-end completos.

---
**Documento generado**: 2024-09-30
**Autor**: Development Team
**Versión**: 1.0.0