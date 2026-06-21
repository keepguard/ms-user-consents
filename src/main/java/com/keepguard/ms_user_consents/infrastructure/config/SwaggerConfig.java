package com.keepguard.ms_user_consents.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";
    private static final String X_APPLICATION_HEADER = "X-Application";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("KeepGuard User Consents API")
                        .version("1.0.0")
                        .description("API de gerenciamento de consentimentos LGPD. " +
                                "Esta API fornece funcionalidades de criação, publicação e arquivamento de " +
                                "documentos de consentimento, além de registro de aceites dos usuários com " +
                                "auditoria completa e conformidade LGPD.")
                        .contact(new Contact()
                                .name("KeepGuard Team")
                                .email("suporte@keepguard.com")
                                .url("https://keepguard.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8086")
                                .description("DEV"),
                        new Server()
                                .url("http://localhost:8586")
                                .description("Local")
                ))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Informe o token JWT gerado pelo ms-auth"))
                        .addSecuritySchemes(X_APPLICATION_HEADER, new SecurityScheme()
                                .name(X_APPLICATION_HEADER)
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("UUID da aplicação que está consumindo a API")))
                .addSecurityItem(new SecurityRequirement()
                        .addList(SECURITY_SCHEME_NAME)
                        .addList(X_APPLICATION_HEADER));
    }
}

