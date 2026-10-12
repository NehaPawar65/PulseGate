package com.pulsegate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;

/** Import into MockMvc tests to send the admin token (from test application.properties) on every request. */
@TestConfiguration
public class AdminTokenMockMvc {

    public static final String TOKEN = "test-admin-token";

    @Bean
    MockMvcBuilderCustomizer adminTokenOnEveryRequest() {
        return builder -> builder.defaultRequest(get("/").header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN));
    }
}
