package com.forge.adapters.inbound.graphql.type;

import java.time.Instant;

/**
 * GraphQL response type for mark username as used operation.
 * Matches the MarkUsedResponse type in schema.graphqls
 */
public record MarkUsedResponse(
        String username,
        boolean marked,
        boolean wasAlreadyUsed,
        Instant markedAt,
        String message
) {

    /**
     * Factory method to create GraphQL response.
     * Converts LocalDateTime to ISO 8601 string for GraphQL compatibility.
     */
    public static MarkUsedResponse of(
        String username,
        boolean marked,
        boolean wasAlreadyUsed,
        Instant markedAt,
        String message
    ) {
        return new MarkUsedResponse(
                username,
                marked,
                wasAlreadyUsed,
                markedAt,
                message);
    }

}
