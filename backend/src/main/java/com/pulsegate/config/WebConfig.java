package com.pulsegate.config;

import com.pulsegate.limiter.RateLimitInterceptor;
import com.pulsegate.security.AdminAuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Admin APIs: require the admin bearer token. /api/health stays public. */
    static final String[] ADMIN_PATHS = {
            "/api/policies/**", "/api/clients/**", "/api/analytics/**", "/api/events/**", "/api/simulations/**"
    };

    private final RateLimitInterceptor rateLimitInterceptor;
    private final AdminAuthInterceptor adminAuthInterceptor;
    private final String[] allowedOrigins;

    public WebConfig(RateLimitInterceptor rateLimitInterceptor, AdminAuthInterceptor adminAuthInterceptor,
                     @Value("${pulsegate.cors.allowed-origins:http://localhost:5173}") String[] allowedOrigins) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.adminAuthInterceptor = adminAuthInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor).addPathPatterns(ADMIN_PATHS);
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
