package com.notifyflow.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3 / Swagger UI configuration.
 *
 * Configures:
 * - API metadata (title, version, contact, license)
 * - Bearer JWT security scheme — adds "Authorize" button to Swagger UI
 * - Server URLs for local dev and Docker environments
 */
@Configuration
public class SwaggerConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Value("${spring.application.name}")
    private String applicationName;

    @Bean
    public OpenAPI notifyFlowOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(serverList())
                .addSecurityItem(
                        new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        SECURITY_SCHEME_NAME,
                                        jwtSecurityScheme()));
    }

    private Info apiInfo() {
        return new Info()
                .title("NotifyFlow API")
                .description("""
                Event-Driven Notification System
                
                Send EMAIL, SMS, and IN_APP notifications via a Kafka-backed pipeline.
                Redis deduplicates messages within a 10-minute window.
                All endpoints (except /auth/**) require a Bearer JWT.
                
                **Quick Start:**
                1. Register via POST /api/auth/register
                2. Login via POST /api/auth/login — copy the token
                3. Click **Authorize** and paste: Bearer <your_token>
                4. Send a notification via POST /api/notifications/send
                """)
                .version("1.0.0")
                .contact(new Contact()
                        .name("Your Name")
                        .email("your.email@example.com")
                        .url("https://linkedin.com/in/your-profile"))
                .license(new License()
                        .name("MIT License")
                        .url("https://opensource.org/licenses/MIT"));
    }

    private List<Server> serverList() {
        return List.of(
                new Server()
                        .url("http://localhost:8080")
                        .description("Local Development"),
                new Server()
                        .url("http://localhost:8080")
                        .description("Docker Compose")
        );
    }

    /**
     * Configures Bearer JWT as the global security scheme.
     * The "Authorize" button in Swagger UI will prompt for a token
     * and prepend "Bearer " automatically to all requests.
     */
    private SecurityScheme jwtSecurityScheme() {
        return new SecurityScheme()
                .name(SECURITY_SCHEME_NAME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description(
                        "Paste your JWT token here (without 'Bearer ' prefix — " +
                                "Swagger adds it automatically)");
    }
}