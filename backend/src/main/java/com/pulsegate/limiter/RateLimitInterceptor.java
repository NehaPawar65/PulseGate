package com.pulsegate.limiter;

import com.pulsegate.entity.Client;
import com.pulsegate.entity.Decision;
import com.pulsegate.entity.RateLimitPolicy;
import com.pulsegate.entity.RequestEvent;
import com.pulsegate.exception.InvalidApiKeyException;
import com.pulsegate.exception.RateLimitExceededException;
import com.pulsegate.repository.ClientRepository;
import com.pulsegate.service.ApiKeyService;
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

    private final ClientRepository clientRepository;
    private final ApiKeyService apiKeyService;
    private final RateLimiterService rateLimiter;
    private final RequestEventRecorder recorder;

    public RateLimitInterceptor(ClientRepository clientRepository, ApiKeyService apiKeyService,
                                RateLimiterService rateLimiter, RequestEventRecorder recorder) {
        this.clientRepository = clientRepository;
        this.apiKeyService = apiKeyService;
        this.rateLimiter = rateLimiter;
        this.recorder = recorder;
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

        Optional<Client> client = (apiKey == null || apiKey.isBlank())
                ? Optional.empty()
                : clientRepository.findByApiKeyHash(apiKeyService.hash(apiKey)).filter(Client::isActive);

        if (client.isEmpty()) {
            record(null, path, Decision.REJECTED_INVALID_KEY, HttpStatus.UNAUTHORIZED.value(), start, null);
            throw new InvalidApiKeyException(apiKey == null || apiKey.isBlank()
                    ? "Missing " + API_KEY_HEADER + " header"
                    : "Invalid or inactive API key");
        }

        Long clientId = client.get().getId();
        RateLimitPolicy policy = client.get().getPolicy();
        LimitSpec spec = new LimitSpec(policy.getAlgorithm(), policy.getCapacity(),
                policy.getRefillTokensPerSecond(), policy.getWindowSeconds());
        ConsumeResult result = rateLimiter.tryConsume(clientId, spec);

        if (!result.allowed()) {
            record(clientId, path, Decision.REJECTED_RATE_LIMIT, HttpStatus.TOO_MANY_REQUESTS.value(), start, 0L);
            throw new RateLimitExceededException(policy.getCapacity(), result.retryAfterSeconds());
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(policy.getCapacity()));
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
