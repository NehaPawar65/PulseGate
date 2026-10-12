package com.pulsegate.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pulsegate.entity.Client;
import com.pulsegate.repository.ClientRepository;
import com.pulsegate.service.ApiKeyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional // each test rolls back
class ClientPolicyApiTest {

    @Autowired MockMvc mvc;
    @Autowired ClientRepository clientRepository;
    @Autowired ApiKeyService apiKeyService;

    private Integer createPolicy(String name) throws Exception {
        String body = mvc.perform(post("/api/policies").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"%s","algorithm":"TOKEN_BUCKET","capacity":5,"refillTokensPerSecond":1.0}
                        """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void registersClientAndReturnsKeyOnlyOnce() throws Exception {
        Integer policyId = createPolicy("api-test-policy");

        String body = mvc.perform(post("/api/clients").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"api-test-client\",\"policyId\":" + policyId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apiKey").value(startsWith("pg_")))
                .andExpect(jsonPath("$.client.policyName").value("api-test-policy"))
                .andReturn().getResponse().getContentAsString();

        String apiKey = JsonPath.read(body, "$.apiKey");
        Client saved = clientRepository.findByApiKeyHash(apiKeyService.hash(apiKey)).orElseThrow();
        assertThat(saved.getApiKeyHash()).isNotEqualTo(apiKey).hasSize(64);

        mvc.perform(get("/api/clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("api-test-client")))
                .andExpect(jsonPath("$[*].apiKey").doesNotExist());
    }

    @Test
    void getsPolicyById() throws Exception {
        Integer id = createPolicy("api-test-get");
        mvc.perform(get("/api/policies/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("api-test-get"));
    }

    @Test
    void rejectsInvalidPolicy() throws Exception {
        mvc.perform(post("/api/policies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"algorithm\":\"TOKEN_BUCKET\",\"capacity\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicatePolicyName() throws Exception {
        createPolicy("api-test-dup");
        mvc.perform(post("/api/policies").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"api-test-dup","algorithm":"TOKEN_BUCKET","capacity":5,"refillTokensPerSecond":1.0}
                        """))
                .andExpect(status().isConflict());
    }

    @Test
    void returns404ForUnknownPolicy() throws Exception {
        mvc.perform(get("/api/policies/999999")).andExpect(status().isNotFound());
        mvc.perform(post("/api/clients").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"orphan\",\"policyId\":999999}"))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest(name = "capacity={0} -> {1}")
    @CsvSource({"1, 201", "100000, 201", "0, 400", "100001, 400", "-5, 400"})
    void capacityBoundaries(int capacity, int expectedStatus) throws Exception {
        mvc.perform(post("/api/policies").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"cap-%d","algorithm":"TOKEN_BUCKET","capacity":%d,"refillTokensPerSecond":1}
                        """.formatted(capacity, capacity)))
                .andExpect(status().is(expectedStatus));
    }

    @ParameterizedTest(name = "windowSeconds={0} -> {1}")
    @CsvSource({"1, 201", "86400, 201", "86401, 400", "0, 400", "'', 400"})
    void fixedWindowBoundaries(String windowSeconds, int expectedStatus) throws Exception {
        String window = windowSeconds.isEmpty() ? "null" : windowSeconds;
        mvc.perform(post("/api/policies").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"win-%s","algorithm":"FIXED_WINDOW","capacity":10,"windowSeconds":%s}
                        """.formatted(window, window)))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void tokenBucketRequiresPositiveRefill() throws Exception {
        mvc.perform(post("/api/policies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"no-refill\",\"algorithm\":\"TOKEN_BUCKET\",\"capacity\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("refillTokensPerSecond must be > 0 for TOKEN_BUCKET"));
    }

    @Test
    void rejectsDuplicateClientName() throws Exception {
        Integer policyId = createPolicy("api-test-dup-client");
        String body = "{\"name\":\"dup-client\",\"policyId\":" + policyId + "}";
        mvc.perform(post("/api/clients").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/clients").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mvc.perform(post("/api/policies").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Malformed JSON request body"));
    }

    @Test
    void nonNumericIdIsBadRequest() throws Exception {
        mvc.perform(get("/api/policies/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid value 'abc' for parameter 'id'"));
    }

    @Test
    void updatesPolicyLimits() throws Exception {
        Integer id = createPolicy("api-test-update");

        mvc.perform(put("/api/policies/" + id).contentType(MediaType.APPLICATION_JSON).content("""
                        {"algorithm":"TOKEN_BUCKET","capacity":50,"refillTokensPerSecond":10}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("api-test-update"))
                .andExpect(jsonPath("$.capacity").value(50))
                .andExpect(jsonPath("$.refillTokensPerSecond").value(10.0));

        mvc.perform(get("/api/policies/" + id)).andExpect(jsonPath("$.capacity").value(50));
    }

    @Test
    void rejectsInvalidPolicyUpdate() throws Exception {
        Integer id = createPolicy("api-test-bad-update");

        mvc.perform(put("/api/policies/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"algorithm\":\"TOKEN_BUCKET\",\"capacity\":5,\"refillTokensPerSecond\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/policies/999999").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"algorithm\":\"TOKEN_BUCKET\",\"capacity\":5,\"refillTokensPerSecond\":1}"))
                .andExpect(status().isNotFound());
    }
}
