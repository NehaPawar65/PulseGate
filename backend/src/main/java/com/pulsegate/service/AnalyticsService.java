package com.pulsegate.service;

import com.pulsegate.dto.AnalyticsSummary;
import com.pulsegate.dto.AnalyticsSummary.ClientStats;
import com.pulsegate.dto.AnalyticsSummary.LatencyStats;
import com.pulsegate.dto.AnalyticsSummary.TimelinePoint;
import com.pulsegate.entity.Client;
import com.pulsegate.entity.Decision;
import com.pulsegate.repository.ClientRepository;
import com.pulsegate.repository.RequestEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Aggregates are computed in Postgres; only small result sets come back to Java. */
@Service
public class AnalyticsService {

    private static final Duration TIMELINE_WINDOW = Duration.ofHours(1);

    private final RequestEventRepository eventRepository;
    private final ClientRepository clientRepository;

    public AnalyticsService(RequestEventRepository eventRepository, ClientRepository clientRepository) {
        this.eventRepository = eventRepository;
        this.clientRepository = clientRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsSummary summary() {
        Map<Decision, Long> totals = new EnumMap<>(Decision.class);
        for (Object[] row : eventRepository.countGroupedByDecision()) {
            totals.put((Decision) row[0], ((Number) row[1]).longValue());
        }
        long allowed = totals.getOrDefault(Decision.ALLOWED, 0L);
        long rateLimited = totals.getOrDefault(Decision.REJECTED_RATE_LIMIT, 0L);
        long invalidKey = totals.getOrDefault(Decision.REJECTED_INVALID_KEY, 0L);
        long total = allowed + rateLimited + invalidKey;

        return new AnalyticsSummary(total, allowed, rateLimited, invalidKey,
                ratio(rateLimited + invalidKey, total), latency(), clientStats(), timeline());
    }

    private LatencyStats latency() {
        Object[] row = eventRepository.latencyStats().getFirst();
        return new LatencyStats(round(asDouble(row[0])), round(asDouble(row[1])), round(asDouble(row[2])),
                round(asDouble(row[3])), (long) asDouble(row[4]));
    }

    private List<ClientStats> clientStats() {
        // clientId -> [allowed, rateLimited]
        Map<Long, long[]> counts = new TreeMap<>();
        for (Object[] row : eventRepository.countGroupedByClientAndDecision()) {
            long[] c = counts.computeIfAbsent((Long) row[0], id -> new long[2]);
            long n = ((Number) row[2]).longValue();
            if (row[1] == Decision.ALLOWED) c[0] += n;
            else if (row[1] == Decision.REJECTED_RATE_LIMIT) c[1] += n;
        }
        Map<Long, String> names = clientRepository.findAllById(counts.keySet()).stream()
                .collect(Collectors.toMap(Client::getId, Client::getName));

        List<ClientStats> stats = new ArrayList<>();
        counts.forEach((id, c) -> stats.add(new ClientStats(id, names.getOrDefault(id, "deleted client"),
                c[0], c[1], ratio(c[1], c[0] + c[1]))));
        return stats;
    }

    private List<TimelinePoint> timeline() {
        // epochSecond -> [allowed, rateLimited, invalidKey]
        Map<Long, long[]> perMinute = new TreeMap<>();
        for (Object[] row : eventRepository.countPerMinuteSince(Instant.now().minus(TIMELINE_WINDOW))) {
            long[] c = perMinute.computeIfAbsent(((Number) row[0]).longValue(), m -> new long[3]);
            int slot = Decision.valueOf((String) row[1]).ordinal();
            c[slot] += ((Number) row[2]).longValue();
        }
        return perMinute.entrySet().stream()
                .map(e -> new TimelinePoint(Instant.ofEpochSecond(e.getKey()),
                        e.getValue()[Decision.ALLOWED.ordinal()],
                        e.getValue()[Decision.REJECTED_RATE_LIMIT.ordinal()],
                        e.getValue()[Decision.REJECTED_INVALID_KEY.ordinal()]))
                .toList();
    }

    private static double ratio(long part, long whole) {
        return whole == 0 ? 0 : Math.round(10_000.0 * part / whole) / 10_000.0;
    }

    private static double asDouble(Object value) {
        return value == null ? 0 : ((Number) value).doubleValue();
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
