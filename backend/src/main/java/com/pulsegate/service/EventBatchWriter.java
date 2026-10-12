package com.pulsegate.service;

import com.pulsegate.entity.RequestEvent;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Inserts request events with one JDBC batch per call instead of one INSERT per event.
 * With reWriteBatchedInserts=true on the JDBC URL, PostgreSQL receives a multi-row INSERT.
 */
@Component
public class EventBatchWriter {

    private static final String INSERT = """
            insert into request_events
                (client_id, path, decision, http_status, latency_micros, remaining_tokens, "timestamp")
            values (?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;

    public EventBatchWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void write(List<RequestEvent> events) {
        jdbc.batchUpdate(INSERT, events, events.size(), (ps, e) -> {
            ps.setObject(1, e.getClientId(), Types.BIGINT);
            ps.setString(2, e.getPath());
            ps.setString(3, e.getDecision().name());
            ps.setInt(4, e.getHttpStatus());
            ps.setLong(5, e.getLatencyMicros());
            ps.setObject(6, e.getRemainingTokens(), Types.BIGINT);
            ps.setObject(7, OffsetDateTime.ofInstant(e.getTimestamp(), ZoneOffset.UTC));
        });
    }
}
