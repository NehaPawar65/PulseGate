package com.pulsegate.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pulsegate.entity.Algorithm;
import com.pulsegate.entity.Client;
import com.pulsegate.entity.Decision;
import com.pulsegate.entity.RateLimitPolicy;
import com.pulsegate.entity.RequestEvent;
import com.pulsegate.repository.ClientRepository;
import com.pulsegate.repository.RateLimitPolicyRepository;
import com.pulsegate.repository.RequestEventRepository;
import com.pulsegate.service.ApiKeyService;
import java.util.List;
import java.util.UUID;
import com.pulsegate.AdminTokenMockMvc;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AdminTokenMockMvc.class)
@Transactional // events written during a request join the test transaction and roll back
class GatewayFlowTest {

    private static final String URL = "/api/protected/resource";

    @Autowired MockMvc mvc;
    @Autowired RateLimitPolicyRepository policyRepository;
    @Autowired ClientRepository clientRepository;
    @Autowired RequestEventRepository eventRepository;
    @Autowired ApiKeyService apiKeyService;

    private Client client;

    /** Creates a client with a capacity-3 policy that effectively never refills during the test. */
    private String newClient(boolean active) {
        return newClient(active, new RateLimitPolicy("gw-" + UUID.randomUUID().toString().substring(0, 8),
                Algorithm.TOKEN_BUCKET, 3, 0.001, null));
    }

    private String newClient(boolean active, RateLimitPolicy unsavedPolicy) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        RateLimitPolicy policy = policyRepository.save(unsavedPolicy);
        String apiKey = apiKeyService.generate();
        client = new Client("gw-client-" + suffix, apiKeyService.hash(apiKey),
                apiKeyService.displayPrefix(apiKey), policy);
        client.setActive(active);
        clientRepository.save(client);
        return apiKey;
    }

    private List<RequestEvent> eventsFor(Long clientId) {
        return eventRepository.findAll().stream().filter(e -> clientId.equals(e.getClientId())).toList();
    }

    @Test
    void allowsRequestsWithinQuotaThenReturns429() throws Exception {
        String apiKey = newClient(true);

        for (int remaining = 2; remaining >= 0; remaining--) {
            mvc.perform(get(URL).header("X-API-Key", apiKey))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Access granted to protected resource"))
                    .andExpect(header().string("X-RateLimit-Limit", "3"))
                    .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(remaining)));
        }

        mvc.perform(get(URL).header("X-API-Key", apiKey))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail", containsString("Rate limit of 3 requests exceeded")))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());

        List<RequestEvent> events = eventsFor(client.getId());
        assertThat(events).extracting(RequestEvent::getDecision)
                .containsExactlyInAnyOrder(Decision.ALLOWED, Decision.ALLOWED, Decision.ALLOWED,
                        Decision.REJECTED_RATE_LIMIT);
        assertThat(events).allSatisfy(e -> {
            assertThat(e.getPath()).isEqualTo(URL);
            assertThat(e.getLatencyMicros()).isNotNegative();
        });
    }

    @Test
    void missingApiKeyReturns401AndIsRecorded() throws Exception {
        long before = eventRepository.countByDecision(Decision.REJECTED_INVALID_KEY);

        mvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Missing X-API-Key header"));

        assertThat(eventRepository.countByDecision(Decision.REJECTED_INVALID_KEY)).isEqualTo(before + 1);
    }

    @Test
    void unknownApiKeyReturns401() throws Exception {
        mvc.perform(get(URL).header("X-API-Key", "pg_not-a-real-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid or inactive API key"));
    }

    @Test
    void inactiveClientReturns401() throws Exception {
        String apiKey = newClient(false);

        mvc.perform(get(URL).header("X-API-Key", apiKey))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientsDoNotShareQuota() throws Exception {
        String first = newClient(true);
        for (int i = 0; i < 3; i++) mvc.perform(get(URL).header("X-API-Key", first));
        mvc.perform(get(URL).header("X-API-Key", first)).andExpect(status().isTooManyRequests());

        String second = newClient(true);
        mvc.perform(get(URL).header("X-API-Key", second)).andExpect(status().isOk());
    }

    @Test
    void nonProtectedEndpointsAreNotRateLimited() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    /** Sends the request from a specific client IP. */
    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    @Test
    void floodOfInvalidKeysIsCutOffBeforeTheDatabase() throws Exception {
        String attacker = "203.0.113.50";
        long before = eventRepository.countByDecision(Decision.REJECTED_INVALID_KEY);

        for (int i = 0; i < 30; i++) { // default allowance: 30 failed attempts
            mvc.perform(get(URL).header("X-API-Key", "pg_guess_" + i).with(from(attacker)))
                    .andExpect(status().isUnauthorized());
        }
        for (int i = 0; i < 20; i++) {
            mvc.perform(get(URL).header("X-API-Key", "pg_guess_more_" + i).with(from(attacker)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(jsonPath("$.detail", containsString("invalid API keys from this address")));
        }

        // Only the first 30 attempts reached the database; the blocked 20 cost nothing.
        assertThat(eventRepository.countByDecision(Decision.REJECTED_INVALID_KEY)).isEqualTo(before + 30);
    }

    @Test
    void blockedAddressDoesNotAffectOtherClients() throws Exception {
        for (int i = 0; i < 31; i++) {
            mvc.perform(get(URL).header("X-API-Key", "pg_bad_" + i).with(from("203.0.113.51")));
        }
        String apiKey = newClient(true);
        mvc.perform(get(URL).header("X-API-Key", apiKey).with(from("198.51.100.20")))
                .andExpect(status().isOk());
    }

    @Test
    void blankApiKeyIsTreatedAsMissing() throws Exception {
        mvc.perform(get(URL).header("X-API-Key", "   "))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Missing X-API-Key header"));
    }

    @Test
    void policyUpdateTakesEffectOnNextRequest() throws Exception {
        String apiKey = newClient(true);
        for (int i = 0; i < 3; i++) mvc.perform(get(URL).header("X-API-Key", apiKey));
        mvc.perform(get(URL).header("X-API-Key", apiKey)).andExpect(status().isTooManyRequests());

        // Through the admin API, as a real admin would: this also clears the client cache.
        mvc.perform(put("/api/policies/" + client.getPolicy().getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"algorithm\":\"TOKEN_BUCKET\",\"capacity\":10,\"refillTokensPerSecond\":0.001}"))
                .andExpect(status().isOk());

        mvc.perform(get(URL).header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "10"))
                .andExpect(header().string("X-RateLimit-Remaining", "9"));
    }

    @Test
    void fixedWindowPolicyIsEnforced() throws Exception {
        String apiKey = newClient(true, new RateLimitPolicy("gw-window-" + UUID.randomUUID().toString().substring(0, 8),
                Algorithm.FIXED_WINDOW, 2, 0, 3600));

        mvc.perform(get(URL).header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", "1"));
        mvc.perform(get(URL).header("X-API-Key", apiKey)).andExpect(status().isOk());

        // Rejected until the hour-long window ends, so Retry-After is close to 3600 s.
        String retryAfter = mvc.perform(get(URL).header("X-API-Key", apiKey))
                .andExpect(status().isTooManyRequests())
                .andReturn().getResponse().getHeader("Retry-After");
        assertThat(Long.parseLong(retryAfter)).isBetween(3500L, 3600L);
    }
}
