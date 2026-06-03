package com.bingo.app.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("BingoPlus API")
                        .version("1.0.0")
                        .description("Multi-tenant Bingo game management API with Telegram authentication"))
                .addSecurityItem(new SecurityRequirement().addList("telegramAuth"))
                .components(new Components()
                        .addSecuritySchemes("telegramAuth", new SecurityScheme()
                                .name("Authorization")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Telegram login widget init data (HMAC-signed)")));
    }
}
