package com.forge.adapters.inbound.graphql.mapper;

import com.forge.adapters.inbound.graphql.input.GenerateUsernameInput;
import com.forge.domain.model.GenerationRequest;
import org.springframework.stereotype.Component;

/**
 * Mapper for GraphQL input to domain models.
 * Converts GraphQL-specific input types to domain entities.
 */
@Component
public class GraphQLRequestMapper {

    /**
     * Convert GraphQL input to domain GenerationRequest
     *
     * @param input GraphQL input from mutation
     * @return Domain GenerationRequest for use case
     */
    public GenerationRequest toDomain(GenerateUsernameInput input) {
        return GenerationRequest.of(
                input.language(),
                input.count()
        );
    }

}
