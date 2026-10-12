package com.pulsegate.dto;

import com.pulsegate.entity.Decision;
import com.pulsegate.entity.RequestEvent;
import java.time.Instant;

public record EventResponse(Long id, Long clientId, String clientName, String path, Decision decision,
                            int httpStatus, long latencyMicros, Long remainingTokens, Instant timestamp) {

    public static EventResponse from(RequestEvent e, String clientName) {
        return new EventResponse(e.getId(), e.getClientId(), clientName, e.getPath(), e.getDecision(),
                e.getHttpStatus(), e.getLatencyMicros(), e.getRemainingTokens(), e.getTimestamp());
    }
}
