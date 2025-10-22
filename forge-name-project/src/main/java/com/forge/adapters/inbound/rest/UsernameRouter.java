package com.forge.adapters.inbound.rest;

import com.forge.adapters.inbound.dto.ValidationResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import com.forge.adapters.inbound.dto.GenerationRequestDto;
import com.forge.adapters.inbound.dto.UsernameResponseDto;
import com.forge.adapters.inbound.dto.ErrorResponseDto;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class UsernameRouter {

    @Bean
    @RouterOperations({
            @RouterOperation(
                    path = "/api/v1/usernames/generate",
                    method = RequestMethod.POST,
                    beanClass = UsernameHandler.class,
                    beanMethod = "generate",
                    operation = @Operation(
                            operationId = "generateUsernames",
                            summary = "Generate Usernames",
                            description = "Generate a specified number of unique, validated usernames in the requested language",
                            tags = {"Username Generation"},
                            requestBody = @RequestBody(
                                    description = "Username generation request parameters",
                                    required = true,
                                    content = @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = GenerationRequestDto.class)
                                    )
                            ),
                            responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "Usernames generated successfully",
                                            content = @Content(
                                                    mediaType = "application/json",
                                                    schema = @Schema(implementation = UsernameResponseDto.class)
                                            )
                                    ),
                                    @ApiResponse(
                                            responseCode = "400",
                                            description = "Invalid request parameters",
                                            content = @Content(
                                                    mediaType = "application/json",
                                                    schema = @Schema(implementation = ErrorResponseDto.class)
                                            )
                                    ),
                                    @ApiResponse(
                                            responseCode = "500",
                                            description = "Internal server error",
                                            content = @Content(
                                                    mediaType = "application/json",
                                                    schema = @Schema(implementation = ErrorResponseDto.class)
                                            )
                                    )
                            }
                    )
            ),
            @RouterOperation(
                    path = "/api/v1/usernames/validate/{username}",
                    method = RequestMethod.GET,
                    beanClass = UsernameHandler.class,
                    beanMethod = "validate",
                    operation = @Operation(
                            operationId = "validateUsername",
                            summary = "Validate Username",
                            description = "Validate username format, uniqueness, and appropriateness",
                            tags = {"Username Validation"},
                            parameters = {
                                    @Parameter(
                                            name = "username",
                                            description = "Username to validate (5-30 chars, [a-z0-9_-])",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(
                                                    type = "string",
                                                    pattern = "^[a-z0-9_-]{5,30}$",
                                                    example = "cleverpanda42"
                                            )
                                    ),
                                    @Parameter(
                                            name = "language",
                                            description = "Language for content moderation",
                                            required = false,
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(
                                                    type = "string",
                                                    allowableValues = {"EN", "ES"},
                                                    defaultValue = "EN"
                                            )
                                    )
                            },
                            responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "Validation result",
                                            content = @Content(
                                                    mediaType = "application/json",
                                                    schema = @Schema(implementation = ValidationResponseDto.class)
                                            )
                                    ),
                                    @ApiResponse(
                                            responseCode = "400",
                                            description = "Invalid request",
                                            content = @Content(
                                                    mediaType = "application/json",
                                                    schema = @Schema(implementation = ErrorResponseDto.class)
                                            )
                                    ),
                                    @ApiResponse(
                                            responseCode = "500",
                                            description = "Internal server error",
                                            content = @Content(
                                                    mediaType = "application/json",
                                                    schema = @Schema(implementation = ErrorResponseDto.class)
                                            )
                                    )
                            }
                    )
            )
    })
    public RouterFunction<ServerResponse> usernamesRouter(UsernameHandler handler) {
        return route()
                .path("/api/v1/usernames", builder ->
                        builder
                                .POST("/generate",
                                        accept(MediaType.APPLICATION_JSON)
                                                .and(contentType(MediaType.APPLICATION_JSON)),
                                        handler::generate)
                                .GET("/validate/{username}",
                                        accept(MediaType.APPLICATION_JSON),
                                        handler::validate)
                )
                .build();
    }

}
