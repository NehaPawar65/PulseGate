package com.pulsegate.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.pulsegate.AdminTokenMockMvc;
import com.pulsegate.entity.Algorithm;
import com.pulsegate.entity.Client;
import com.pulsegate.entity.RateLimitPolicy;
import com.pulsegate.repository.ClientRepository;
import com.pulsegate.repository.RateLimitPolicyRepository;
import com.pulsegate.repository.RequestEventRepository;
import com.pulsegate.service.ApiKeyService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Runs on a real port: the simulator sends real HTTP requests to this server.
 * Not @Transactional (requests run on other threads), so each test uses unique names.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SimulationApiTest {

    @LocalServerPort int port;
    @Autowired RateLimitPolicyRepository policyRepository;
    @Autowired ClientRepository clientRepository;
    @Autowired RequestEventRepository eventRepository;
    @Autowired ApiKeyService apiKeyService;

    private final HttpClient http = HttpClient.newHttpClient();

    private record Created(Client client, String apiKey) {}

    /** Capacity 10 with a refill so slow that no tokens are added during the test. */
    private Created newClient() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        RateLimitPolicy policy = policyRepository.save(
                new RateLimitPolicy("sim-" + suffix, Algorithm.TOKEN_BUCKET, 10, 0.001, null));
        String apiKey = apiKeyService.generate();
        Client client = clientRepository.save(new Client("sim-client-" + suffix, apiKeyService.hash(apiKey),
                apiKeyService.displayPrefix(apiKey), policy));
        return new Created(client, apiKey);
    }

    private HttpResponse<String> simulate(String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/simulations"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + AdminTokenMockMvc.TOKEN)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void concurrentWorkloadIsLimitedToCapacity() throws Exception {
        Created c = newClient();

        HttpResponse<String> response = simulate("""
                {"apiKey":"%s","totalRequests":50,"concurrency":10}
                """.formatted(c.apiKey()));

        assertThat(response.statusCode()).isEqualTo(200);
        DocumentContext result = JsonPath.parse(response.body());
        assertThat(result.read("$.totalRequests", Integer.class)).isEqualTo(50);
        assertThat(result.read("$.allowed", Integer.class)).isEqualTo(10);
        assertThat(result.read("$.rateLimited", Integer.class)).isEqualTo(40);
        assertThat(result.read("$.errors", Integer.class)).isZero();
        assertThat(result.read("$.rejectionRate", Double.class)).isEqualTo(0.8);

        double p50 = result.read("$.latency.p50", Double.class);
        double p95 = result.read("$.latency.p95", Double.class);
        double max = result.read("$.latency.max", Double.class);
        assertThat(p50).isPositive().isLessThanOrEqualTo(p95);
        assertThat(p95).isLessThanOrEqualTo(max);

        List<Integer> allowedPerBucket = result.read("$.timeline[*].allowed");
        List<Integer> rejectedPerBucket = result.read("$.timeline[*].rejected");
        assertThat(allowedPerBucket.stream().mapToInt(Integer::intValue).sum()).isEqualTo(10);
        assertThat(rejectedPerBucket.stream().mapToInt(Integer::intValue).sum()).isEqualTo(40);

        // Every simulated request went through the gateway and was recorded.
        long recorded = eventRepository.findAll().stream()
                .filter(e -> c.client().getId().equals(e.getClientId())).count();
        assertThat(recorded).isEqualTo(50);
    }

    @Test
    void invalidKeyIsReportedAsUnauthorized() throws Exception {
        HttpResponse<String> response = simulate("""
                {"apiKey":"pg_wrong","totalRequests":5,"concurrency":2}
                """);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.parse(response.body()).read("$.unauthorized", Integer.class)).isEqualTo(5);
    }

    @Test
    void rejectsOutOfRangeParameters() throws Exception {
        assertThat(simulate("{\"apiKey\":\"x\",\"totalRequests\":10,\"concurrency\":0}").statusCode())
                .isEqualTo(400);
        assertThat(simulate("{\"apiKey\":\"x\",\"totalRequests\":100000,\"concurrency\":10}").statusCode())
                .isEqualTo(400);
        assertThat(simulate("{\"totalRequests\":10,\"concurrency\":1}").statusCode())
                .isEqualTo(400);
    }
}
