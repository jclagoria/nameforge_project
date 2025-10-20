package com.forge.adapters.outbound.database.entity;

import com.forge.domain.model.Language;
import com.forge.domain.model.PatternType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;


@DisplayName("UsernameEntity Tests")
class UsernameEntityTest {

    @Test
    @DisplayName("Should Generate Username Entity")
    void shouldGenerateUsernameEntity() {
        UsernameEntity entity = UsernameEntity.builder()
                .username("cleverpanda42")
                .language(Language.EN)
                .patternType(PatternType.CLASSIC)
                .createdAt(LocalDateTime.now())
                .isUsed(false)
                .build();

        assertThat(entity).isNotNull();
        assertThat(entity.getUsername()).isEqualTo("cleverpanda42");
        assertThat(entity.getLanguage()).isEqualTo(Language.EN);
        assertThat(entity.getPatternType()).isEqualTo(PatternType.CLASSIC);
        assertThat(entity.isUsed()).isFalse();
    }

}