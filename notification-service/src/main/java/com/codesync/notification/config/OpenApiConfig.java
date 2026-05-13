package com.codesync.notification.config;

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
 * OpenAPI / Swagger configuration for the Notification Service.
 * Accessible at: http://localhost:8088/swagger-ui/index.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI notificationServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CodeSync - Notification Service API")
                        .description("""
                                In-app and email notifications for all CodeSync platform events.
                                
                                Notification types: COMMENT, MENTION, VERSION, EXECUTION, COLLAB, PROJECT, SYSTEM
                                
                                Channels:
                                  - In-app: persisted to DB, polled or pushed via the inbox API
                                  - Email: sent via SMTP (JavaMailSender) based on user preferences
                                
                                Endpoints:
                                  - Create notification (called by other services)
                                  - Read inbox (paginated, filtered by type or unread)
                                  - Mark as read (single or all)
                                  - Soft-delete
                                  - Manage per-type email/in-app preferences
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
