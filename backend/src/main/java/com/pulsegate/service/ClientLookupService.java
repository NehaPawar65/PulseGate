package com.pulsegate.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.pulsegate.entity.Client;
import com.pulsegate.entity.RateLimitPolicy;
import com.pulsegate.limiter.LimitSpec;
import com.pulsegate.repository.ClientRepository;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Resolves an API-key hash to the client and its policy, cached in memory so repeat
 * requests skip the database. Entries expire after a short TTL; policy changes clear
 * the cache on this instance immediately (other instances catch up within the TTL).
 */
@Service
public class ClientLookupService {

    /** What the gateway needs about a client: plain values, safe to share between threads. */
    public record CachedClient(long id, boolean active, LimitSpec spec) {

        static CachedClient from(Client client) {
            RateLimitPolicy p = client.getPolicy();
            return new CachedClient(client.getId(), client.isActive(),
                    new LimitSpec(p.getAlgorithm(), p.getCapacity(), p.getRefillTokensPerSecond(), p.getWindowSeconds()));
        }
    }

    private final ClientRepository clientRepository;
    private final Cache<String, CachedClient> cache;

    public ClientLookupService(ClientRepository clientRepository,
                               @Value("${pulsegate.client-cache.ttl:30s}") Duration ttl,
                               @Value("${pulsegate.client-cache.max-size:10000}") long maxSize) {
        this.clientRepository = clientRepository;
        this.cache = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxSize).build();
    }

    /** Unknown keys are not cached: InvalidKeyGuard already limits how often they can be tried. */
    public Optional<CachedClient> findByKeyHash(String apiKeyHash) {
        CachedClient cached = cache.getIfPresent(apiKeyHash);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<CachedClient> loaded = clientRepository.findByApiKeyHash(apiKeyHash).map(CachedClient::from);
        loaded.ifPresent(client -> cache.put(apiKeyHash, client));
        return loaded;
    }

    /**
     * Clears the cache now and again after the surrounding transaction commits, so a request
     * that reloads old data between the two can't keep it for a whole TTL.
     */
    public void invalidateAll() {
        cache.invalidateAll();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cache.invalidateAll();
                }
            });
        }
    }

    long size() {
        cache.cleanUp();
        return cache.estimatedSize();
    }
}
