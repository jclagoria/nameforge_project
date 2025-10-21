package com.forge.adapters.inbound.graphql.mapper;

import com.forge.adapters.inbound.graphql.type.UsernameGenerationResponse;
import com.forge.domain.model.GenerationResponse;
import org.springframework.stereotype.Component;

/**
 * Mapper for domain models to GraphQL response types.
 * Converts domain entities to GraphQL-specific response structures.
 */
@Component
public class GraphQLResponseMapper {

    /**
     * Convert domain GenerationResponse to GraphQL response type
     *
     * @param domainResponse Domain response from use case
     * @return GraphQL response type
     */
    public UsernameGenerationResponse toGraphQL(GenerationResponse domainResponse) {
        return UsernameGenerationResponse.of(
                domainResponse.getUsernameValues(),
                domainResponse.generatedAt(),
                domainResponse.language(),
                domainResponse.totalGenerated(),
                domainResponse.cacheHit(),
                domainResponse.responseTimeMs()
        );
    }

}
