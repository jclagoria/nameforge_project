package com.forge.adapters.outbound.database.mapper;

import com.forge.adapters.outbound.database.entity.UsernameEntity;
import com.forge.domain.model.Language;
import com.forge.domain.model.PatternType;
import com.forge.domain.model.Username;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UsernameEntityMapper Tests")
class UsernameEntityMapperTest {

    private UsernameEntityMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new UsernameEntityMapper();
    }

    @Test
    @DisplayName("Should Map Domain To Entity")
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
    @DisplayName("Should Map Entity To Domain")
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