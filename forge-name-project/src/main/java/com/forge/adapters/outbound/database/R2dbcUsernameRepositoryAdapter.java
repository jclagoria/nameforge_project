package com.forge.adapters.outbound.database;

import com.forge.adapters.outbound.database.entity.UsernameEntity;
import com.forge.adapters.outbound.database.mapper.UsernameEntityMapper;
import com.forge.domain.model.Language;
import com.forge.domain.model.PatternType;
import com.forge.domain.model.Username;
import com.forge.domain.ports.outboung.UsernameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class R2dbcUsernameRepositoryAdapter implements UsernameRepository {

    private final DatabaseClient  databaseClient;
    private final R2dbcEntityTemplate entityTemplate;
    private final UsernameEntityMapper mapper;

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
        return entityTemplate
                .insert(entity)
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

    private UsernameEntity mapRowToEntity(Map<String, Object> row) {
        String patternTypeStr = (String) row.get("pattern_type");
        return UsernameEntity.builder()
                .id( ((Number) row.get("id")).longValue())
                .username((String) row.get("username"))
                .language(Language.valueOf( (String) row.get("language")))
                .patternType(patternTypeStr != null ? PatternType.valueOf(patternTypeStr) : null)
                .createdAt((LocalDateTime) row.get("created_at"))
                .isUsed((boolean) row.get("is_used"))
                .usedAt((LocalDateTime) row.get("used_at"))
                .build();
    }
}
