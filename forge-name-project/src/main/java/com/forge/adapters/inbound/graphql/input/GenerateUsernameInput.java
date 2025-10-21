package com.forge.adapters.inbound.graphql.input;

import com.forge.domain.model.Language;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * GraphQL input type for username generation requests.
 * Matches the GenerateUsernamesInput type in schema.graphqls
 */
public record GenerateUsernameInput(

        @NotNull(message = "Language is required")
        Language language,

        @Min(value = 1, message = "Count must be at least 1")
        @Max(value = 10, message = "Count must be at most 10")
        Integer count
) {

    /**
     * Canonical constructor with validation and default values
     */
    public GenerateUsernameInput {
        if (count == null) {
            count = 1;
        }
    }

}
