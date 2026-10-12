package com.pulsegate.dto;

import java.util.List;

/**
 * Outcome of one simulated workload. Latency is client-observed round-trip time
 * (includes HTTP and the gateway), in milliseconds.
 */
public record SimulationResult(
        int totalRequests,
        int concurrency,
        long allowed,
        long rateLimited,
        long unauthorized,
        long errors,
        double rejectionRate,
        long durationMillis,
        double throughputPerSecond,
        LatencyMillis latency,
        List<TimelineBucket> timeline) {

    public record LatencyMillis(double avg, double p50, double p95, double p99, double max) {}

    /** Requests started in [offsetMillis, offsetMillis + bucket size), grouped by outcome. */
    public record TimelineBucket(long offsetMillis, long allowed, long rejected) {}
}
