package com.forge.domain.model;

import java.time.LocalDateTime;

/**
 * Result of marking a username as used operation.
 */
public record MarkUsedResult(
   String username,
   boolean wasAlreadyUsed,
   LocalDateTime markedAt
) {

    public static MarkUsedResult alreadyUsed(String username) {
        return new MarkUsedResult(username, true, null);
    }

    public static MarkUsedResult marked(String username, LocalDateTime markedAt) {
        return new MarkUsedResult(username, false, markedAt);
    }

}
