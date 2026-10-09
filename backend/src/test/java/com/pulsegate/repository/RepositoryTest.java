package com.pulsegate.repository;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import com.pulsegate.entity.Algorithm;
import com.pulsegate.entity.Client;
import com.pulsegate.entity.Decision;
import com.pulsegate.entity.RateLimitPolicy;
import com.pulsegate.entity.RequestEvent;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // use the real Postgres; there is no H2
class RepositoryTest {

    @Autowired RateLimitPolicyRepository policyRepository;
    @Autowired ClientRepository clientRepository;
    @Autowired RequestEventRepository eventRepository;

    @Test
    void findsClientByApiKeyHashWithPolicy() {
        RateLimitPolicy policy = policyRepository.save(
                new RateLimitPolicy("test-free", Algorithm.TOKEN_BUCKET, 10, 1.0, null));
        String hash = "a".repeat(64);
        clientRepository.save(new Client("test-client", hash, "pg_abcde", policy));

        Client found = clientRepository.findByApiKeyHash(hash).orElseThrow();

        assertThat(found.getName()).isEqualTo("test-client");
        assertThat(found.getPolicy().getName()).isEqualTo("test-free");
        assertThat(found.isActive()).isTrue();
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void countsEventsByDecision() {
        long before = eventRepository.countByDecision(Decision.REJECTED_RATE_LIMIT);
        Instant now = Instant.now();
        eventRepository.save(new RequestEvent(1L, "/api/protected/resource", Decision.ALLOWED, 200, 120, 9L, now));
        eventRepository.save(new RequestEvent(1L, "/api/protected/resource", Decision.REJECTED_RATE_LIMIT, 429, 80, 0L, now));
        eventRepository.save(new RequestEvent(1L, "/api/protected/resource", Decision.REJECTED_RATE_LIMIT, 429, 75, 0L, now));

        assertThat(eventRepository.countByDecision(Decision.REJECTED_RATE_LIMIT)).isEqualTo(before + 2);
    }
}
