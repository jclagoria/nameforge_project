package com.forge.adapters.inbound.graphql.resolver;

import com.forge.adapters.inbound.graphql.input.GenerateUsernameInput;
import com.forge.adapters.inbound.graphql.mapper.GraphQLRequestMapper;
import com.forge.adapters.inbound.graphql.mapper.GraphQLResponseMapper;
import com.forge.adapters.inbound.graphql.type.UsernameGenerationResponse;
import com.forge.domain.ports.inbound.UsernameGenerationUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

/**
 * GraphQL Mutation Resolver for username operations.
 * Implements the Mutation type from schema.graphqls
 *
 * This is an inbound adapter in hexagonal architecture,
 * translating GraphQL requests to domain use cases.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class UsernameMutationResolver {

    private final UsernameGenerationUseCase usernameGenerationUseCase;
    private final GraphQLRequestMapper requestMapper;
    private final GraphQLResponseMapper responseMapper;

    /**
     * GraphQL mutation: generateUsernames
     *
     * Generates usernames based on language and count parameters.
     *
     * GraphQL Query Example:
     * mutation {
     *   generateUsernames(input: { language: EN, count: 5 }) {
     *     usernames
     *     generatedAt
     *     language
     *     totalGenerated
     *     cacheHit
     *     responseTimeMs
     *   }
     * }
     *
     * @param input Validated input containing language and count
     * @return Mono of UsernameGenerationResponse with generated usernames and metadata
     */
    @MutationMapping(name = "generateUsernames")
    public Mono<UsernameGenerationResponse> generateUsernames(
            @Argument @Valid GenerateUsernameInput input
    ) {
        log.info("GraphQL mutation: generateUsernames - language={}, count={}",
                input.language(), input.count());

        return Mono.just(input)
                .map(requestMapper::toDomain)
                .flatMap(usernameGenerationUseCase::generate)
                .map(responseMapper::toGraphQL)
                .doOnSuccess(response ->
                        log.info("GraphQL mutation completed - generated {} usernames, cacheHit={}",
                                response.totalGenerated(), response.cacheHit())
                )
                .doOnError(error ->
                        log.error("GraphQL mutation failed", error)
                );
    }

}
