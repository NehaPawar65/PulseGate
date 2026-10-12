package com.pulsegate.dto;

import java.time.Instant;
import java.util.List;

/**
 * Aggregate view of recorded gateway decisions.
 * rejectionRate = (rateLimited + invalidKey) / totalRequests.
 */
public record AnalyticsSummary(
        long totalRequests,
        long allowed,
        long rateLimited,
        long invalidKey,
        double rejectionRate,
        LatencyStats gatewayLatencyMicros,
        List<ClientStats> clients,
        List<TimelinePoint> lastHour) {

    /** Server-side time to decide and serve a request, in microseconds. */
    public record LatencyStats(double avg, double p50, double p95, double p99, long max) {}

    /** Per-client counts; rejectionRate = rateLimited / (allowed + rateLimited). */
    public record ClientStats(Long clientId, String clientName, long allowed, long rateLimited,
                              double rejectionRate) {}

    public record TimelinePoint(Instant minute, long allowed, long rateLimited, long invalidKey) {}
}
