package com.pulsegate.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
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
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional // the delete + inserts below roll back after each test
class EventsAndAnalyticsApiTest {

    private static final String PATH = "/api/protected/resource";

    @Autowired MockMvc mvc;
    @Autowired RateLimitPolicyRepository policyRepository;
    @Autowired ClientRepository clientRepository;
    @Autowired RequestEventRepository eventRepository;

    private Client client;

    /** Known data set: 3 allowed + 1 rate-limited for one client, 1 invalid key. */
    @BeforeEach
    public void seed() {
        eventRepository.deleteAllInBatch();
        RateLimitPolicy policy = policyRepository.save(
                new RateLimitPolicy("analytics-policy", Algorithm.TOKEN_BUCKET, 3, 1.0, null));
        client = clientRepository.save(new Client("analytics-client", "b".repeat(64), "pg_analy", policy));

        Instant now = Instant.now();
        save(client.getId(), Decision.ALLOWED, 200, 100, now);
        save(client.getId(), Decision.ALLOWED, 200, 200, now);
        save(client.getId(), Decision.ALLOWED, 200, 300, now);
        save(client.getId(), Decision.REJECTED_RATE_LIMIT, 429, 50, now);
        save(null, Decision.REJECTED_INVALID_KEY, 401, 10, now);
    }

    private void save(Long clientId, Decision decision, int status, long latencyMicros, Instant at) {
        eventRepository.save(new RequestEvent(clientId, PATH, decision, status, latencyMicros,
                decision == Decision.ALLOWED ? 1L : null, at));
    }

    @Test
    void summaryAggregatesAllDecisions() throws Exception {
        mvc.perform(get("/api/analytics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(5))
                .andExpect(jsonPath("$.allowed").value(3))
                .andExpect(jsonPath("$.rateLimited").value(1))
                .andExpect(jsonPath("$.invalidKey").value(1))
                .andExpect(jsonPath("$.rejectionRate").value(0.4))
                .andExpect(jsonPath("$.gatewayLatencyMicros.max").value(300))
                .andExpect(jsonPath("$.gatewayLatencyMicros.p50").value(100.0))
                .andExpect(jsonPath("$.gatewayLatencyMicros.avg").value(132.0))
                .andExpect(jsonPath("$.clients", hasSize(1)))
                .andExpect(jsonPath("$.clients[0].clientName").value("analytics-client"))
                .andExpect(jsonPath("$.clients[0].allowed").value(3))
                .andExpect(jsonPath("$.clients[0].rateLimited").value(1))
                .andExpect(jsonPath("$.clients[0].rejectionRate").value(0.25))
                .andExpect(jsonPath("$.lastHour", hasSize(1)))
                .andExpect(jsonPath("$.lastHour[0].allowed").value(3))
                .andExpect(jsonPath("$.lastHour[0].rateLimited").value(1))
                .andExpect(jsonPath("$.lastHour[0].invalidKey").value(1));
    }

    @Test
    void summaryIsZeroWhenThereAreNoEvents() throws Exception {
        eventRepository.deleteAllInBatch();

        mvc.perform(get("/api/analytics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(0))
                .andExpect(jsonPath("$.rejectionRate").value(0.0))
                .andExpect(jsonPath("$.gatewayLatencyMicros.p95").value(0.0))
                .andExpect(jsonPath("$.clients", hasSize(0)))
                .andExpect(jsonPath("$.lastHour", hasSize(0)));
    }

    @Test
    void eventsAreNewestFirstWithClientNames() throws Exception {
        mvc.perform(get("/api/events").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].decision").value("REJECTED_INVALID_KEY"))
                .andExpect(jsonPath("$[0].clientName").doesNotExist())
                .andExpect(jsonPath("$[1].decision").value("REJECTED_RATE_LIMIT"))
                .andExpect(jsonPath("$[1].clientName").value("analytics-client"));
    }

    @Test
    void eventsCanBeFilteredByClient() throws Exception {
        mvc.perform(get("/api/events").param("clientId", client.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)));
    }

    @Test
    void eventLimitIsClampedToValidRange() throws Exception {
        mvc.perform(get("/api/events").param("limit", "0")).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/events").param("limit", "-10")).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/events").param("limit", "1000000")).andExpect(jsonPath("$", hasSize(5)));
    }

    @Test
    void unknownClientFilterReturnsEmptyList() throws Exception {
        mvc.perform(get("/api/events").param("clientId", "999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void deleteClearsEventHistory() throws Exception {
        mvc.perform(delete("/api/events")).andExpect(status().isNoContent());
        mvc.perform(get("/api/events")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void corsAllowsTheViteDevServer() throws Exception {
        mvc.perform(options("/api/protected/resource")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "X-API-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

        mvc.perform(options("/api/clients")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
