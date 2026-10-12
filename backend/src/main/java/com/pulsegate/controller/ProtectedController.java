package com.pulsegate.controller;

import com.pulsegate.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sample upstream API. Only reached when RateLimitInterceptor allows the request. */
@RestController
@RequestMapping("/api/protected")
public class ProtectedController {

    @GetMapping("/resource")
    @SecurityRequirement(name = OpenApiConfig.API_KEY_SCHEME)
    public Map<String, Object> resource() {
        return Map.of(
                "message", "Access granted to protected resource",
                "timestamp", Instant.now());
    }
}
