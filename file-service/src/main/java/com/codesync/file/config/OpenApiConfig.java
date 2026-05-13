package com.codesync.file.config;

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
 * OpenAPI / Swagger configuration for the File Service.
 * Accessible at: http://localhost:8083/swagger-ui/index.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fileServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CodeSync – File Service API")
                        .description("""
                                Manages the source file and directory tree for each CodeSync project.
                                
                                **Key features:**
                                - Create files and directories
                                - Read file content and project tree
                                - Update (rename, move, edit content)
                                - Soft-delete files and directory trees
                                - Keyword search across file names and content
                                
                                All endpoints receive the authenticated user ID via the 
                                `X-User-Id` header forwarded by the API Gateway.
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
                                .description("JWT from POST /api/auth/login")));
    }
}
