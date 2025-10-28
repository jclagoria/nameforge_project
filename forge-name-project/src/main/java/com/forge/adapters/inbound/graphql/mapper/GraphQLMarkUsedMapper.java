package com.forge.adapters.inbound.graphql.mapper;

import com.forge.adapters.inbound.graphql.type.MarkUsedResponse;
import com.forge.domain.model.MarkUsedResult;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;

/**
 * Mapper for converting domain MarkUsedResult to GraphQL MarkUsedResponse.
 *
 * This adapter component transforms domain models into GraphQL-specific
 * response types, maintaining separation of concerns in hexagonal architecture.
 */
@Component
public class GraphQLMarkUsedMapper {

    public MarkUsedResponse toGraphQL(MarkUsedResult domainResult) {
        boolean marked = !domainResult.wasAlreadyUsed();
        String message = domainResult.wasAlreadyUsed()
                ? "Username was already used"
                : "Username successfully marked as used";

        var markedAt = domainResult.markedAt() != null
                ? domainResult.markedAt().toInstant(ZoneOffset.UTC)
                : null;

        return MarkUsedResponse.of(
                domainResult.username(),
                marked,
                domainResult.wasAlreadyUsed(),
                markedAt,
                message
        );
    }

}
