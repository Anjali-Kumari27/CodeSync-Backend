package com.codesync.version.config;

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
 * OpenAPI / Swagger configuration for the Version Service.
 * Accessible at: http://localhost:8086/swagger-ui/index.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI versionServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CodeSync – Version Service API")
                        .description("""
                                Git-inspired version control for CodeSync projects.
                                
                                **Key features:**
                                - Create and manage branches per project
                                - Commit file snapshots with SHA-256 hashes
                                - Full commit history (per branch or per project)
                                - Tag commits as versioned releases (e.g. v1.0.0)
                                - Diff any two commits to see what changed
                                
                                All endpoints receive the authenticated user ID via the
                                `X-User-Id` header forwarded by the API Gateway.
                                """)
                        .version("v1.0.0")
                        .contact(new Contact().name("CodeSync Team").email("support@codesync.dev"))
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT from POST /api/auth/login")));
    }
}
