package com.pulsegate.repository;

import com.pulsegate.entity.Decision;
import com.pulsegate.entity.RequestEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequestEventRepository extends JpaRepository<RequestEvent, Long> {
    List<RequestEvent> findTop100ByOrderByTimestampDesc();
    long countByDecision(Decision decision);

    List<RequestEvent> findAllByOrderByIdDesc(Pageable pageable);

    List<RequestEvent> findByClientIdOrderByIdDesc(Long clientId, Pageable pageable);

    /** Rows of [Decision, Long count]. */
    @Query("select e.decision, count(e) from RequestEvent e group by e.decision")
    List<Object[]> countGroupedByDecision();

    /** Rows of [Long clientId, Decision, Long count]. */
    @Query("""
            select e.clientId, e.decision, count(e) from RequestEvent e
            where e.clientId is not null
            group by e.clientId, e.decision
            """)
    List<Object[]> countGroupedByClientAndDecision();

    /** One row of [avg, p50, p95, p99, max] gateway latency in microseconds; values are null when empty. */
    @Query(value = """
            select avg(latency_micros),
                   percentile_cont(0.50) within group (order by latency_micros),
                   percentile_cont(0.95) within group (order by latency_micros),
                   percentile_cont(0.99) within group (order by latency_micros),
                   max(latency_micros)
            from request_events
            """, nativeQuery = true)
    List<Object[]> latencyStats();

    /** Rows of [epochSecond of the minute, decision name, count] for events since the given time. */
    @Query(value = """
            select cast(extract(epoch from date_trunc('minute', "timestamp")) as bigint),
                   decision,
                   count(*)
            from request_events
            where "timestamp" >= :since
            group by 1, 2
            order by 1
            """, nativeQuery = true)
    List<Object[]> countPerMinuteSince(@Param("since") Instant since);
}
