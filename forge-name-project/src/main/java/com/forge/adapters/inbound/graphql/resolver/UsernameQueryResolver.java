package com.forge.adapters.inbound.graphql.resolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

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

}
