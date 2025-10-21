package com.forge.adapters.inbound.graphql.type;

import com.forge.domain.model.Language;

import java.time.Instant;
import java.util.List;

/**
 * GraphQL response type for username generation.
 * Matches the UsernameGenerationResponse type in schema.graphqls
 */
public record UsernameGenerationResponse(
        List<String> usernames,
        String generatedAt,
        Language language,
        int totalGenerated,
        boolean cacheHit,
        long responseTimeMs
) {

    public static UsernameGenerationResponse of(
            List<String> usernames,
            Instant generatedAt,
            Language language,
            int totalGenerated,
            boolean cacheHit,
            long responseTimeMs
    ) {
        return  new UsernameGenerationResponse(
                usernames,
                generatedAt.toString(),
                language,
                totalGenerated,
                cacheHit,
                responseTimeMs
        );
    }

}
