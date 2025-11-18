package org.example.magiclink.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenAPIConfig {

    @Value("${app.magic-link.base-url}")
    private String baseUrl;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Magic Link Service API")
                        .version("1.0.0")
                        .description("API documentation for Magic Link authentication service with OAuth integration")
                        .contact(new Contact()
                                .name("Magic Link Service")
                                .email("support@example.com")))
                .servers(List.of(
                        new Server().url(baseUrl).description("Production Server"),
                        new Server().url("http://localhost:18080").description("Local Development Server")
                ));
    }

    @Bean
    public GroupedOpenApi formApi() {
        return GroupedOpenApi.builder()
                .group("form-registration")
                .pathsToMatch("/form/**")
                .build();
    }

    @Bean
    public GroupedOpenApi allApi() {
        return GroupedOpenApi.builder()
                .group("all-endpoints")
                .pathsToMatch("/**")
                .build();
    }
}
