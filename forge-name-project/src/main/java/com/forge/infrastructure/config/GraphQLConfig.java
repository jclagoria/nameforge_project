package com.forge.infrastructure.config;

import graphql.scalars.ExtendedScalars;
import graphql.schema.GraphQLScalarType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * GraphQL configuration for custom scalars and runtime wiring.
 */
@Configuration
public class GraphQLConfig {

    /**
     * Configure custom GraphQL scalars.
     * Adds support for Long scalar type used in response metadata.
     */
    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiringBuilder -> wiringBuilder.scalar(longScalar());
    }

    /**
     * Define Long scalar type for responseTimeMs field.
     * GraphQL doesn't have built-in Long support.
     */
    private GraphQLScalarType longScalar() {
        return GraphQLScalarType.newScalar()
                .name("Long")
                .description("Long type")
                .coercing(ExtendedScalars.GraphQLLong.getCoercing())
                .build();
    }

}
