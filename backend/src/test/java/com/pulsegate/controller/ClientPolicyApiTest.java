package com.pulsegate.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pulsegate.entity.Client;
import com.pulsegate.repository.ClientRepository;
import com.pulsegate.service.ApiKeyService;
import org.junit.jupiter.api.Test;
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
}
