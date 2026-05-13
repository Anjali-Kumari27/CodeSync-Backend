package com.codesync.comment.config;

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
 * OpenAPI / Swagger configuration for the Comment Service.
 * Accessible at: http://localhost:8087/swagger-ui/index.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI commentServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CodeSync – Comment Service API")
                        .description("""
                                Inline code comments, review threads, and emoji reactions for CodeSync projects.
                                
                                **Features:**
                                - File-level and inline (line + column) code comments
                                - Two-level thread hierarchy (root comments + replies)
                                - Resolve and reopen comment threads
                                - Soft-delete with [deleted] placeholder
                                - GitHub-style emoji reactions per comment
                                - Paginated views by file, line, project, or keyword
                                """)
                        .version("v1.0.0")
                        .contact(new Contact().name("CodeSync Team").email("support@codesync.dev"))
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
