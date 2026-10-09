package com.pulsegate.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "rate_limit_policies")
public class RateLimitPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Algorithm algorithm;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private double refillTokensPerSecond;

    private Integer windowSeconds;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected RateLimitPolicy() {}

    public RateLimitPolicy(String name, Algorithm algorithm, int capacity,
                           double refillTokensPerSecond, Integer windowSeconds) {
        this.name = name;
        this.algorithm = algorithm;
        this.capacity = capacity;
        this.refillTokensPerSecond = refillTokensPerSecond;
        this.windowSeconds = windowSeconds;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Algorithm getAlgorithm() { return algorithm; }
    public int getCapacity() { return capacity; }
    public double getRefillTokensPerSecond() { return refillTokensPerSecond; }
    public Integer getWindowSeconds() { return windowSeconds; }
    public Instant getCreatedAt() { return createdAt; }

    public void setAlgorithm(Algorithm algorithm) { this.algorithm = algorithm; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public void setRefillTokensPerSecond(double r) { this.refillTokensPerSecond = r; }
    public void setWindowSeconds(Integer windowSeconds) { this.windowSeconds = windowSeconds; }
}
