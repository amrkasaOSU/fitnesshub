package com.fitnesshub.config;

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
    public OpenAPI fitnessHubOpenApi() {
        final String cookieAuth = "cookieAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("FitnessHub API")
                        .description("Full-stack fitness coaching, workout tracking, nutrition, "
                                + "progression, and coach-client communication platform.")
                        .version("v0.1.0"))
                .addSecurityItem(new SecurityRequirement().addList(cookieAuth))
                .components(new Components().addSecuritySchemes(cookieAuth,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")));
    }
}
