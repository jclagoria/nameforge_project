package com.forge.adapters.outbound.database.mapper;

import com.forge.adapters.outbound.database.entity.UsernameEntity;
import com.forge.domain.model.PatternType;
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
        PatternType patternType = entity.getPatternType() != null
                ? entity.getPatternType()
                : PatternType.CLASSIC;

        return Username.of(
                entity.getUsername(),
                entity.getLanguage(),
                patternType
        );
    }

}
