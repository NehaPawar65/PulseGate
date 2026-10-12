package com.pulsegate.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pulsegate.AdminTokenMockMvc;
import com.pulsegate.entity.Algorithm;
import com.pulsegate.entity.Client;
import com.pulsegate.entity.RateLimitPolicy;
import com.pulsegate.limiter.RateLimiterService;
import com.pulsegate.limiter.RedisRateLimiterService;
import com.pulsegate.repository.ClientRepository;
import com.pulsegate.repository.RateLimitPolicyRepository;
import com.pulsegate.service.ApiKeyService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** The full gateway with pulsegate.limiter.store=redis. */
@SpringBootTest(properties = "pulsegate.limiter.store=redis")
@AutoConfigureMockMvc
@Import(AdminTokenMockMvc.class)
@Transactional
class RedisGatewayTest {

    private static final String URL = "/api/protected/resource";

    @Autowired MockMvc mvc;
    @Autowired RateLimiterService rateLimiter;
    @Autowired RateLimitPolicyRepository policyRepository;
    @Autowired ClientRepository clientRepository;
    @Autowired ApiKeyService apiKeyService;

    @Test
    void redisStoreIsSelectedByConfiguration() {
        assertThat(rateLimiter).isInstanceOf(RedisRateLimiterService.class);
    }

    @Test
    void gatewayEnforcesLimitsThroughRedis() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        RateLimitPolicy policy = policyRepository.save(
                new RateLimitPolicy("redis-" + suffix, Algorithm.TOKEN_BUCKET, 3, 0.001, null));
        String apiKey = apiKeyService.generate();
        clientRepository.save(new Client("redis-client-" + suffix, apiKeyService.hash(apiKey),
                apiKeyService.displayPrefix(apiKey), policy));

        for (int remaining = 2; remaining >= 0; remaining--) {
            mvc.perform(get(URL).header("X-API-Key", apiKey))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(remaining)));
        }
        mvc.perform(get(URL).header("X-API-Key", apiKey))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }
}
