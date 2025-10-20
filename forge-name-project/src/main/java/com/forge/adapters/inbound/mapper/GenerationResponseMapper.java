package com.forge.adapters.inbound.mapper;

import com.forge.adapters.inbound.dto.UsernameResponseDto;
import com.forge.domain.model.GenerationResponse;
import org.springframework.stereotype.Component;

@Component
public class GenerationResponseMapper {

    public UsernameResponseDto toDto(GenerationResponse domain) {
        return new UsernameResponseDto(
                domain.getUsernameValues(),
                domain.generatedAt(),
                domain.language().name(),
                domain.totalGenerated(),
                domain.cacheHit(),
                domain.responseTimeMs()
        );
    }
}
