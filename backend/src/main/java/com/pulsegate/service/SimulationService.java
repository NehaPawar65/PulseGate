package com.pulsegate.service;

import com.pulsegate.dto.SimulationRequest;
import com.pulsegate.dto.SimulationResult;
import com.pulsegate.dto.SimulationResult.LatencyMillis;
import com.pulsegate.dto.SimulationResult.TimelineBucket;
import com.pulsegate.limiter.RateLimitInterceptor;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * Closed-loop load generator: {@code concurrency} workers each send a request, wait for
 * the response, then send the next, until {@code totalRequests} have been sent. Requests
 * go over real HTTP to the protected endpoint, so they pass through the full gateway path.
 */
@Service
public class SimulationService {

    private static final int STATUS_ERROR = -1;
    private static final long TIMELINE_BUCKET_MILLIS = 100;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public SimulationResult run(SimulationRequest req, URI target) {
        int total = req.totalRequests();
        int concurrency = Math.min(req.concurrency(), total);
        HttpRequest request = HttpRequest.newBuilder(target)
                .header(RateLimitInterceptor.API_KEY_HEADER, req.apiKey())
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        // Each index is written by exactly one worker; Future.get() publishes the writes to this thread.
        int[] statuses = new int[total];
        long[] latencyNanos = new long[total];
        long[] startOffsetNanos = new long[total];
        AtomicInteger next = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        long runStart = System.nanoTime();
        try {
            List<Future<?>> workers = new ArrayList<>();
            for (int w = 0; w < concurrency; w++) {
                workers.add(pool.submit(() -> {
                    int i;
                    while ((i = next.getAndIncrement()) < total) {
                        long start = System.nanoTime();
                        statuses[i] = send(request);
                        latencyNanos[i] = System.nanoTime() - start;
                        startOffsetNanos[i] = start - runStart;
                    }
                }));
            }
            for (Future<?> worker : workers) {
                worker.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Simulation interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Simulation worker failed", e.getCause());
        } finally {
            pool.shutdownNow();
        }
        long durationNanos = System.nanoTime() - runStart;

        return summarize(total, req.concurrency(), statuses, latencyNanos, startOffsetNanos, durationNanos);
    }

    private int send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException e) {
            return STATUS_ERROR;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return STATUS_ERROR;
        }
    }

    private static SimulationResult summarize(int total, int concurrency, int[] statuses, long[] latencyNanos,
                                              long[] startOffsetNanos, long durationNanos) {
        long allowed = 0, rateLimited = 0, unauthorized = 0, errors = 0;
        for (int status : statuses) {
            switch (status) {
                case 200 -> allowed++;
                case 429 -> rateLimited++;
                case 401 -> unauthorized++;
                default -> errors++;
            }
        }

        long[] sorted = latencyNanos.clone();
        Arrays.sort(sorted);
        LatencyMillis latency = new LatencyMillis(
                millis(Arrays.stream(sorted).average().orElse(0)),
                millis(percentile(sorted, 0.50)),
                millis(percentile(sorted, 0.95)),
                millis(percentile(sorted, 0.99)),
                millis(sorted[sorted.length - 1]));

        double durationMillis = durationNanos / 1_000_000.0;
        double throughput = durationMillis == 0 ? 0 : Math.round(total / (durationMillis / 1000) * 10) / 10.0;

        return new SimulationResult(total, concurrency, allowed, rateLimited, unauthorized, errors,
                Math.round(10_000.0 * (total - allowed) / total) / 10_000.0,
                Math.round(durationMillis), throughput, latency,
                timeline(statuses, startOffsetNanos));
    }

    private static List<TimelineBucket> timeline(int[] statuses, long[] startOffsetNanos) {
        long bucketNanos = TIMELINE_BUCKET_MILLIS * 1_000_000;
        int buckets = (int) (Arrays.stream(startOffsetNanos).max().orElse(0) / bucketNanos) + 1;
        long[] allowed = new long[buckets];
        long[] rejected = new long[buckets];
        for (int i = 0; i < statuses.length; i++) {
            int b = (int) (startOffsetNanos[i] / bucketNanos);
            if (statuses[i] == 200) allowed[b]++;
            else rejected[b]++;
        }
        List<TimelineBucket> result = new ArrayList<>(buckets);
        for (int b = 0; b < buckets; b++) {
            result.add(new TimelineBucket(b * TIMELINE_BUCKET_MILLIS, allowed[b], rejected[b]));
        }
        return result;
    }

    /** Nearest-rank percentile on a sorted array. */
    private static double percentile(long[] sorted, double p) {
        int rank = (int) Math.ceil(p * sorted.length);
        return sorted[Math.max(0, rank - 1)];
    }

    private static double millis(double nanos) {
        return Math.round(nanos / 10_000.0) / 100.0;
    }
}
