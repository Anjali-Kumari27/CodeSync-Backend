package com.codesync.execution.config;

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
 * OpenAPI / Swagger configuration for the Execution Service.
 * Accessible at: http://localhost:8085/swagger-ui/index.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI executionServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CodeSync - Execution Service API")
                        .description("""
                                Sandboxed multi-language code execution engine for CodeSync.
                                
                                Supported languages: JAVA, PYTHON, JAVASCRIPT, TYPESCRIPT,
                                C, CPP, GO, RUST, KOTLIN, BASH
                                
                                Execution limits:
                                  - Timeout     : 10 seconds (configurable)
                                  - Max output  : 512 KB (configurable)
                                  - Max code    : 100,000 characters
                                
                                Security model:
                                  - Code runs in an isolated OS subprocess.
                                  - Environment variables are cleared before execution.
                                  - Temp files are deleted after each run.
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
