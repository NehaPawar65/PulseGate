package com.pulsegate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulsegate.entity.Algorithm;
import com.pulsegate.entity.Client;
import com.pulsegate.entity.RateLimitPolicy;
import com.pulsegate.repository.ClientRepository;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ClientLookupServiceTest {

    private final ClientRepository repository = mock(ClientRepository.class);

    private Client client(long id, int capacity) {
        RateLimitPolicy policy = new RateLimitPolicy("p", Algorithm.TOKEN_BUCKET, capacity, 1.0, null);
        Client c = new Client("c", "h".repeat(64), "pg_abcde", policy);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    @Test
    void repeatLookupsAreServedFromTheCache() {
        when(repository.findByApiKeyHash("hash")).thenReturn(Optional.of(client(7, 5)));
        ClientLookupService lookup = new ClientLookupService(repository, Duration.ofSeconds(30), 100);

        for (int i = 0; i < 100; i++) {
            assertThat(lookup.findByKeyHash("hash")).get()
                    .satisfies(c -> {
                        assertThat(c.id()).isEqualTo(7);
                        assertThat(c.spec().capacity()).isEqualTo(5);
                    });
        }
        verify(repository, times(1)).findByApiKeyHash("hash"); // 1 database query for 100 requests
    }

    @Test
    void unknownKeysAreNotCached() {
        when(repository.findByApiKeyHash("nope")).thenReturn(Optional.empty());
        ClientLookupService lookup = new ClientLookupService(repository, Duration.ofSeconds(30), 100);

        assertThat(lookup.findByKeyHash("nope")).isEmpty();
        assertThat(lookup.findByKeyHash("nope")).isEmpty();
        verify(repository, times(2)).findByApiKeyHash("nope");
        assertThat(lookup.size()).isZero();
    }

    @Test
    void invalidateAllReloadsUpdatedPolicy() {
        when(repository.findByApiKeyHash("hash")).thenReturn(Optional.of(client(7, 5)));
        ClientLookupService lookup = new ClientLookupService(repository, Duration.ofSeconds(30), 100);
        lookup.findByKeyHash("hash");

        when(repository.findByApiKeyHash("hash")).thenReturn(Optional.of(client(7, 50)));
        lookup.invalidateAll();

        assertThat(lookup.findByKeyHash("hash")).get().satisfies(c -> assertThat(c.spec().capacity()).isEqualTo(50));
    }

    @Test
    void entriesExpireAfterTheTtl() throws Exception {
        when(repository.findByApiKeyHash("hash")).thenReturn(Optional.of(client(7, 5)));
        ClientLookupService lookup = new ClientLookupService(repository, Duration.ofMillis(50), 100);

        lookup.findByKeyHash("hash");
        Thread.sleep(120);
        lookup.findByKeyHash("hash");
        verify(repository, times(2)).findByApiKeyHash("hash");
    }
}
