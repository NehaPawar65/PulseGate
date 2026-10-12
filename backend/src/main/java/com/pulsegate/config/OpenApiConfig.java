package com.pulsegate.config;

import com.pulsegate.limiter.RateLimitInterceptor;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String API_KEY_SCHEME = "apiKey";

    @Bean
    OpenAPI pulseGateOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("PulseGate API")
                        .version("0.1.0")
                        .description("API traffic gateway with per-client rate limiting"))
                .components(new Components().addSecuritySchemes(API_KEY_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(RateLimitInterceptor.API_KEY_HEADER)));
    }
}
