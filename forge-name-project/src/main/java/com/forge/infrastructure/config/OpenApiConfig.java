package com.forge.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI forgeNameOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("NameForge API")
                        .description("Reactive Username Generation System - REST API Documentation")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("NameForge Team")
                                .email("support@nameforge.com")
                                .url("https://nameforge.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local Development Server"),
                        new Server()
                                .url("https://api.nameforge.com")
                                .description("Production Server")
                ));
    }

    @Bean
    public GroupedOpenApi usernamesApi() {
        return GroupedOpenApi.builder()
                .group("usernames")
                .pathsToMatch("/api/v1/usernames/**")
                .displayName("Username Generation API")
                .build();
    }

    @Bean
    public GroupedOpenApi actuatorApi() {
        return GroupedOpenApi.builder()
                .group("actuator")
                .pathsToMatch("/actuator/**")
                .displayName("Actuator Monitoring API")
                .build();
    }
}
