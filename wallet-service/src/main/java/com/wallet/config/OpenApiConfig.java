package com.wallet.config;

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
 * Конфигурация OpenAPI 3.
 *
 * springdoc-openapi автоматически сканирует @RestController.
 * Здесь дополняем: метаданные, security scheme (JWT Bearer),
 * чтобы в Swagger UI была кнопка "Authorize".
 *
 * После запуска:
 * - Swagger UI:      http://localhost:8080/swagger-ui.html
 * - OpenAPI JSON:    http://localhost:8080/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI walletServiceOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Wallet Service API")
                .version("v1")
                .description("""
                    Electronic wallet backend.

                    - Регистрация и JWT-аутентификация.
                    - Управление кошельками пользователя.
                    - Операции: deposit / withdraw / transfer (идемпотентные).
                    - История операций с пагинацией.

                    Аутентификация: получите JWT через POST /api/v1/auth/login,
                    нажмите Authorize (иконка замка справа сверху) и вставьте токен.
                    """)
                .contact(new Contact().name("Wallet Team"))
                .license(new License().name("MIT")))
            // Глобальное требование Bearer JWT
            .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
            .components(new Components()
                .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                    .name(SECURITY_SCHEME_NAME)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT токен из /api/v1/auth/login. Формат: только сам токен, без 'Bearer '.")));
    }
}
