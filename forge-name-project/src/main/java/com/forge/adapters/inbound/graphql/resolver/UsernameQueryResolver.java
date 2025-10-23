package com.forge.adapters.inbound.graphql.resolver;

import com.forge.adapters.inbound.graphql.mapper.GraphQLValidationMapper;
import com.forge.adapters.inbound.graphql.type.ValidationResponse;
import com.forge.domain.model.Language;
import com.forge.domain.model.ValidationRequest;
import com.forge.domain.ports.inbound.UsernameValidationUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

/**
 * GraphQL Query Resolver for username operations.
 * Implements the Query type from schema.graphqls
 *
 * This is an inbound adapter in hexagonal architecture,
 * providing query operations for the GraphQL API.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class UsernameQueryResolver {

    private final UsernameValidationUseCase usernameValidationUseCase;
    private final GraphQLValidationMapper validationMapper;

    /**
     * GraphQL query: _health
     *
     * Health check query to verify the GraphQL API is functioning.
     *
     * GraphQL Query Example:
     * query {
     *   _health
     * }
     *
     * @return Always returns true to indicate API is healthy
     */
    @QueryMapping(name = "_health")
    public Boolean health() {
        log.debug("GraphQL query: _health");
        return true;
    }

    /**
     * GraphQL query: validateUsername
     *
     * Validates a username based on format, uniqueness, and appropriateness.
     * Uses the existing domain validation use case.
     *
     * GraphQL Query Example:
     * query {
     *   validateUsername(username: "cleverpanda42", language: EN) {
     *     username
     *     isValid
     *     isUnique
     *     isAppropriate
     *     isValidFormat
     *     reasons
     *     confidenceScore
     *     validatedAt
     *   }
     * }
     *
     * @param username Username to validate (5-30 characters, alphanumeric + _ -)
     * @param language Language context for validation (defaults to EN)
     * @return Mono of ValidationResponse with validation results
     */
    @QueryMapping(name = "validateUsername")
    public Mono<ValidationResponse> validateUsername(
            @Argument String username,
            @Argument (name = "language")Language language
    ) {
        log.debug("GraphQL query: validateUsername - username={}, language={}", username, language);

        Language validationLanguage = language != null ? language : Language.EN;

        ValidationRequest request = ValidationRequest.of(username, validationLanguage);

        return usernameValidationUseCase.validate(request)
                .map(validationMapper::toGraphQL)
                .doOnSuccess(response -> log.debug(
                        "Validation completed - username={}, isValid={}, reasons={}",
                        response.username(),
                        response.isValid(),
                        response.reasons()
                ));
    }

}
