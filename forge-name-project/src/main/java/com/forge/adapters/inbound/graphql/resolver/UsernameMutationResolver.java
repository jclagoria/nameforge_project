package com.forge.adapters.inbound.graphql.resolver;

import com.forge.adapters.inbound.graphql.input.GenerateUsernameInput;
import com.forge.adapters.inbound.graphql.mapper.GraphQLMarkUsedMapper;
import com.forge.adapters.inbound.graphql.mapper.GraphQLRequestMapper;
import com.forge.adapters.inbound.graphql.mapper.GraphQLResponseMapper;
import com.forge.adapters.inbound.graphql.type.MarkUsedResponse;
import com.forge.adapters.inbound.graphql.type.UsernameGenerationResponse;
import com.forge.domain.ports.inbound.UsernameGenerationUseCase;
import com.forge.domain.ports.inbound.UsernameMarkUsedUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
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
@Validated
public class UsernameMutationResolver {

    private final UsernameGenerationUseCase usernameGenerationUseCase;
    private final UsernameMarkUsedUseCase usernameMarkUsedUseCase;
    private final GraphQLRequestMapper requestMapper;
    private final GraphQLResponseMapper responseMapper;
    private final GraphQLMarkUsedMapper markUsedMapper;

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

    /**
     * GraphQL mutation: markUsernameAsUsed
     *
     * Marks a username as used (claimed by a user).
     * Updates database and invalidates cache layers.
     *
     * GraphQL Mutation Example:
     * mutation {
     *   markUsernameAsUsed(username: "cleverpanda42") {
     *     username
     *     marked
     *     wasAlreadyUsed
     *     markedAt
     *     message
     *   }
     * }
     *
     * @param username Username to mark as used (5-30 chars, [a-z0-9_-])
     * @return Mono of MarkUsedResponse with operation result
     */
    @MutationMapping(name = "markUsernameAsUsed")
    public Mono<MarkUsedResponse> markUsernameAsUsed(
            @Argument
            @NotBlank(message = "Username is required")
            @Pattern(
                    regexp = "^[a-z0-9_-]{5,30}$",
                    message = "Username must be 5-30 characters (lowercase, numbers, underscore, hyphen)"
            )
            String username
    ) {
        log.info("GraphQL mutation: markUsernameAsUsed - username={}", username);

        return usernameMarkUsedUseCase.markAsUsed(username)
                .map(markUsedMapper::toGraphQL)
                .doOnSuccess(response ->
                        log.info("GraphQL mutation completed - username={}, marked={}, wasAlreadyUsed={}",
                                response.username(), response.marked(), response.wasAlreadyUsed())
                ).doOnError(error ->
                        log.error("GraphQL mutation failed for username: {}", username, error)
                );
    }

}
