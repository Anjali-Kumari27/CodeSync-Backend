package com.codesync.project.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration for the Project Service.
 * Accessible at: http://localhost:8082/swagger-ui/index.html
 * Or via the API Gateway: http://localhost:8080/swagger-ui.html (Project Service tab)
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI projectServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CodeSync – Project Service API")
                        .description("""
                                Manages collaborative coding projects and their membership.
                                
                                **Authorization**: All endpoints (except public project reads) require
                                the `X-User-Id` header forwarded by the API Gateway after JWT validation.
                                Use the **Authorize** button to set your Bearer token when testing
                                directly against this service.
                                """)
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("CodeSync Team")
                                .email("support@codesync.dev"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste your JWT token here. Obtain one via POST /api/auth/login")));
    }
}
