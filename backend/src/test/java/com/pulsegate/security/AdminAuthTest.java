package com.pulsegate.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pulsegate.AdminTokenMockMvc;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** No default admin header here: every request states its own credentials. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminAuthTest {

    private static final String VALID = "Bearer " + AdminTokenMockMvc.TOKEN;

    @Autowired MockMvc mvc;

    @ParameterizedTest(name = "GET {0} without token -> 401")
    @ValueSource(strings = {"/api/policies", "/api/clients", "/api/analytics/summary", "/api/events"})
    void adminReadsRequireToken(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.detail").value("Missing admin token"));
    }

    @Test
    void adminWritesRequireToken() throws Exception {
        mvc.perform(post("/api/policies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"algorithm\":\"TOKEN_BUCKET\",\"capacity\":5,\"refillTokensPerSecond\":1}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/events")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/simulations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"pg_x\",\"totalRequests\":1,\"concurrency\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongTokenIsRejected() throws Exception {
        mvc.perform(get("/api/policies").header(HttpHeaders.AUTHORIZATION, "Bearer not-the-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid admin token"));
        mvc.perform(get("/api/policies").header(HttpHeaders.AUTHORIZATION, AdminTokenMockMvc.TOKEN)) // no "Bearer "
                .andExpect(status().isUnauthorized());
    }

    @Test
    void correctTokenIsAccepted() throws Exception {
        mvc.perform(get("/api/policies").header(HttpHeaders.AUTHORIZATION, VALID)).andExpect(status().isOk());
        mvc.perform(get("/api/analytics/summary").header(HttpHeaders.AUTHORIZATION, VALID)).andExpect(status().isOk());
    }

    @Test
    void healthStaysPublic() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void protectedEndpointUsesApiKeysNotTheAdminToken() throws Exception {
        // The admin token is not a client API key.
        mvc.perform(get("/api/protected/resource").header(HttpHeaders.AUTHORIZATION, VALID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Missing X-API-Key header"));
    }

    @Test
    void corsPreflightNeedsNoToken() throws Exception {
        mvc.perform(options("/api/clients")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}
