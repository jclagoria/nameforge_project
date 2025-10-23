package com.forge.adapters.inbound.graphql.mapper;

import com.forge.adapters.inbound.graphql.type.ValidationResponse;
import com.forge.domain.model.ValidationRequest;
import com.forge.domain.model.ValidationResult;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting domain ValidationResult to GraphQL ValidationResponse.
 *
 * This adapter component transforms domain models into GraphQL-specific
 * response types, maintaining separation of concerns in hexagonal architecture.
 */
@Component
public class GraphQLValidationMapper {

    /**
     * Convert domain ValidationResult to GraphQL response type.
     *
     * @param domainResult Domain validation result from use case
     * @return GraphQL ValidationResponse
     */
    public ValidationResponse toGraphQL(ValidationResult domainResult) {
        return ValidationResponse.of(
                domainResult.username(),
                domainResult.isValid(),
                domainResult.isUnique(),
                domainResult.isAppropriate(),
                domainResult.isValidFormat(),
                domainResult.reasons(),
                domainResult.confidenceScore(),
                domainResult.validatedAt()
        );
    }

}
