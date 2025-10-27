package com.forge.adapters.inbound.mapper;

import com.forge.adapters.inbound.dto.MarkUsedResponseDto;
import com.forge.domain.model.MarkUsedResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;

@Component
public class MarkUsedResponseMapper {

    public MarkUsedResponseDto toDto(MarkUsedResult result) {
        boolean marked = !result.wasAlreadyUsed();
        String message = result.wasAlreadyUsed()
                ? "Username was already used"
                : "Username successfully marked as used";

        Instant markedAt = result.markedAt() != null
                ? result.markedAt().toInstant(ZoneOffset.UTC)
                : null;

        return new MarkUsedResponseDto(
                result.username(),
                marked,
                result.wasAlreadyUsed(),
                markedAt,
                message
        );
    }

}
