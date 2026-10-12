package com.pulsegate.limiter;

import com.pulsegate.entity.Decision;
import com.pulsegate.entity.RequestEvent;
import com.pulsegate.exception.InvalidApiKeyException;
import com.pulsegate.exception.RateLimitExceededException;
import com.pulsegate.exception.TooManyInvalidKeyAttemptsException;
import com.pulsegate.service.ApiKeyService;
import com.pulsegate.service.ClientLookupService;
import com.pulsegate.service.ClientLookupService.CachedClient;
import com.pulsegate.service.RequestEventRecorder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Gateway check for /api/protected/**: identify the client by API key,
 * apply its rate limit, and record the decision.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    public static final String API_KEY_HEADER = "X-API-Key";

    private static final String ATTR_PREFIX = RateLimitInterceptor.class.getName() + ".";
    private static final String START_ATTR = ATTR_PREFIX + "start";
    private static final String CLIENT_ID_ATTR = ATTR_PREFIX + "clientId";
    private static final String REMAINING_ATTR = ATTR_PREFIX + "remaining";

    private final ClientLookupService clientLookup;
    private final ApiKeyService apiKeyService;
    private final RateLimiterService rateLimiter;
    private final RequestEventRecorder recorder;
    private final InvalidKeyGuard invalidKeyGuard;

    public RateLimitInterceptor(ClientLookupService clientLookup, ApiKeyService apiKeyService,
                                RateLimiterService rateLimiter, RequestEventRecorder recorder,
                                InvalidKeyGuard invalidKeyGuard) {
        this.clientLookup = clientLookup;
        this.apiKeyService = apiKeyService;
        this.rateLimiter = rateLimiter;
        this.recorder = recorder;
        this.invalidKeyGuard = invalidKeyGuard;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Browser CORS preflights never carry the API key; let Spring's CORS handling answer them.
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }
        long start = System.nanoTime();
        String path = request.getRequestURI();
        String apiKey = request.getHeader(API_KEY_HEADER);
        String ip = request.getRemoteAddr();

        // An address that keeps sending bad keys is stopped here: no DB lookup, no event insert.
        if (invalidKeyGuard.isBlocked(ip)) {
            throw new TooManyInvalidKeyAttemptsException(invalidKeyGuard.retryAfterSeconds());
        }

        // Cached: repeat requests from a known client skip the database entirely.
        Optional<CachedClient> client = (apiKey == null || apiKey.isBlank())
                ? Optional.empty()
                : clientLookup.findByKeyHash(apiKeyService.hash(apiKey)).filter(CachedClient::active);

        if (client.isEmpty()) {
            invalidKeyGuard.recordFailure(ip);
            record(null, path, Decision.REJECTED_INVALID_KEY, HttpStatus.UNAUTHORIZED.value(), start, null);
            throw new InvalidApiKeyException(apiKey == null || apiKey.isBlank()
                    ? "Missing " + API_KEY_HEADER + " header"
                    : "Invalid or inactive API key");
        }

        long clientId = client.get().id();
        LimitSpec spec = client.get().spec();
        ConsumeResult result = rateLimiter.tryConsume(clientId, spec);

        if (!result.allowed()) {
            record(clientId, path, Decision.REJECTED_RATE_LIMIT, HttpStatus.TOO_MANY_REQUESTS.value(), start, 0L);
            throw new RateLimitExceededException(spec.capacity(), result.retryAfterSeconds());
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(spec.capacity()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remainingTokens()));
        request.setAttribute(START_ATTR, start);
        request.setAttribute(CLIENT_ID_ATTR, clientId);
        request.setAttribute(REMAINING_ATTR, result.remainingTokens());
        return true;
    }

    // Runs after the protected handler, so allowed-request latency includes the handler's work.
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        Object start = request.getAttribute(START_ATTR);
        if (start == null) {
            return;
        }
        record((Long) request.getAttribute(CLIENT_ID_ATTR), request.getRequestURI(), Decision.ALLOWED,
                response.getStatus(), (Long) start, (Long) request.getAttribute(REMAINING_ATTR));
    }

    private void record(Long clientId, String path, Decision decision, int status, long startNanos,
                        Long remaining) {
        long latencyMicros = (System.nanoTime() - startNanos) / 1_000;
        recorder.record(new RequestEvent(clientId, path, decision, status, latencyMicros, remaining, Instant.now()));
    }
}
