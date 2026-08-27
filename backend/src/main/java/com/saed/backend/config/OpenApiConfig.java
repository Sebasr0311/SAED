package com.saed.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.media.StringSchema;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI saedOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("SAED 2.0 REST API")
                        .description("API Documentation for SAED 2.0 (Sistema de Administracion de Edificios). Features JWT Auth, Multi-tenancy (X-Tenant-Id) and Oracle RLS Zero-Trust Isolation.")
                        .version("v2.0.0")
                        .contact(new Contact().name("SAED Dev Team").url("https://github.com/Sebasr0311/SAED"))
                        .license(new License().name("Private").url("https://example.com/license")))
                .components(new Components()
                        .addSecuritySchemes("bearer-jwt", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)
                                .name("Authorization")
                                .description("JWT Token for Authentication")))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
    }

    @Bean
    public OpenApiCustomizer customerGlobalHeaderOpenApiCustomizer() {
        return openApi -> openApi.getPaths().values().forEach(pathItem -> pathItem.readOperations().forEach(operation -> {
            operation.addParametersItem(new Parameter()
                    .in("header")
                    .schema(new StringSchema())
                    .name("X-Assignment-Id")
                    .description("ID of the user assignment resolving to a specific Organization/Property context. Validated by SaedContext filter.")
                    .required(false));
            operation.addParametersItem(new Parameter()
                    .in("header")
                    .schema(new StringSchema())
                    .name("X-Tenant-Id")
                    .description("Global Organization context. Optional if X-Assignment-Id implicitly resolves it.")
                    .required(false));
        }));
    }
}
