package com.pulsegate.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "clients")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    // SHA-256 hex of the API key. The raw key is never stored.
    @Column(nullable = false, unique = true, length = 64)
    private String apiKeyHash;

    @Column(nullable = false, length = 8)
    private String apiKeyPrefix;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private RateLimitPolicy policy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Client() {}

    public Client(String name, String apiKeyHash, String apiKeyPrefix, RateLimitPolicy policy) {
        this.name = name;
        this.apiKeyHash = apiKeyHash;
        this.apiKeyPrefix = apiKeyPrefix;
        this.policy = policy;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getApiKeyHash() { return apiKeyHash; }
    public String getApiKeyPrefix() { return apiKeyPrefix; }
    public boolean isActive() { return active; }
    public RateLimitPolicy getPolicy() { return policy; }
    public Instant getCreatedAt() { return createdAt; }

    public void setActive(boolean active) { this.active = active; }
    public void setPolicy(RateLimitPolicy policy) { this.policy = policy; }
}
