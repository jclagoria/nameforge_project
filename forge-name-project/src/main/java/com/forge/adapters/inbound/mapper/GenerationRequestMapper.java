package com.forge.adapters.inbound.mapper;

import com.forge.adapters.inbound.dto.GenerationRequestDto;
import com.forge.domain.model.GenerationRequest;
import com.forge.domain.model.Language;
import org.springframework.stereotype.Component;

@Component
public class GenerationRequestMapper {

    public GenerationRequest toDomain(GenerationRequestDto dto) {
        Language language = parseLanguage(dto.language());
        return new GenerationRequest(language, dto.count());
    }

    private Language parseLanguage(String languageStr) {
        try {
            return Language.valueOf(languageStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid language: " + languageStr + ". Must be one of: EN, ES",
                    e
            );
        }
    }

}
