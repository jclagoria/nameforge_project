package com.forge.adapters.inbound.mapper;

import com.forge.adapters.inbound.dto.ValidationResponseDto;
import com.forge.domain.model.ValidationResult;
import org.springframework.stereotype.Component;

@Component
public class ValidationResponseMapper {

    public ValidationResponseDto toDto(ValidationResult result) {
        return new ValidationResponseDto(
                result.username(),
                result.isValid(),
                result.isUnique(),
                result.isAppropriate(),
                result.isValidFormat(),
                result.reasons(),
                result.confidenceScore(),
                result.validatedAt()
        );
    }

}
