package com.pulsegate.security;

import com.pulsegate.exception.AdminAuthenticationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Guards the admin APIs with a bearer token: {@code Authorization: Bearer <token>}.
 * The token comes from PULSEGATE_ADMIN_TOKEN; if unset, a random one is generated
 * per run and printed once at startup (local development only).
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthInterceptor.class);
    private static final String BEARER = "Bearer ";

    private final byte[] expectedToken;

    public AdminAuthInterceptor(@Value("${pulsegate.admin.token:}") String configuredToken) {
        String token = configuredToken.isBlank() ? generateToken() : configuredToken.trim();
        if (configuredToken.isBlank()) {
            log.warn("PULSEGATE_ADMIN_TOKEN is not set. Admin token for this run: {}"
                    + "  (set PULSEGATE_ADMIN_TOKEN to use a fixed token)", token);
        }
        this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            throw new AdminAuthenticationException("Missing admin token");
        }
        byte[] given = header.substring(BEARER.length()).trim().getBytes(StandardCharsets.UTF_8);
        // Constant-time comparison: response time doesn't reveal how much of the token matched.
        if (!MessageDigest.isEqual(given, expectedToken)) {
            throw new AdminAuthenticationException("Invalid admin token");
        }
        return true;
    }

    private static String generateToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return "adm_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
