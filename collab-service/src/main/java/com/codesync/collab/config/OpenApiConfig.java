package com.codesync.collab.config;

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
 * OpenAPI / Swagger configuration for the Collab Service.
 * Accessible at: http://localhost:8084/swagger-ui/index.html
 *
 * Note: WebSocket (STOMP) endpoints are not shown in Swagger.
 * This documents the HTTP REST management endpoints only.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI collabServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CodeSync - Collaboration Service API")
                        .description("""
                                Real-time collaborative code editing for CodeSync.

                                WebSocket (STOMP) - Real-time channel (not in Swagger UI):
                                  Connect  : ws://localhost:8084/ws/collab
                                  Subscribe: /topic/session/{sessionId}    (receive edits)
                                  Send edit: /app/collab/edit              (broadcast edits)
                                  Presence : /app/collab/presence          (join/leave)

                                HTTP REST - Session management (documented below):
                                - Create or join a session for a file
                                - Get active participants in a session
                                - Close a session manually
                                - List sessions for a project or file
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
