package com.pulsegate.config;

import com.pulsegate.limiter.RateLimitInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final String[] allowedOrigins;

    public WebConfig(RateLimitInterceptor rateLimitInterceptor,
                     @Value("${pulsegate.cors.allowed-origins:http://localhost:5173}") String[] allowedOrigins) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/protected/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*")
                // Let browser code read the rate-limit headers.
                .exposedHeaders("X-RateLimit-Limit", "X-RateLimit-Remaining", "Retry-After");
    }
}
