package com.pulsegate.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "request_events", indexes = {
        @Index(name = "idx_events_timestamp", columnList = "timestamp"),
        @Index(name = "idx_events_client_id", columnList = "client_id")
})
public class RequestEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Plain column, not a relation: null for invalid keys, cheap to insert.
    @Column(name = "client_id")
    private Long clientId;

    @Column(nullable = false)
    private String path;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Decision decision;

    @Column(nullable = false)
    private int httpStatus;

    @Column(nullable = false)
    private long latencyMicros;

    private Long remainingTokens;

    @Column(nullable = false)
    private Instant timestamp;

    protected RequestEvent() {}

    public RequestEvent(Long clientId, String path, Decision decision, int httpStatus,
                        long latencyMicros, Long remainingTokens, Instant timestamp) {
        this.clientId = clientId;
        this.path = path;
        this.decision = decision;
        this.httpStatus = httpStatus;
        this.latencyMicros = latencyMicros;
        this.remainingTokens = remainingTokens;
        this.timestamp = timestamp;
    }

    public Long getId() { return id; }
    public Long getClientId() { return clientId; }
    public String getPath() { return path; }
    public Decision getDecision() { return decision; }
    public int getHttpStatus() { return httpStatus; }
    public long getLatencyMicros() { return latencyMicros; }
    public Long getRemainingTokens() { return remainingTokens; }
    public Instant getTimestamp() { return timestamp; }
}
