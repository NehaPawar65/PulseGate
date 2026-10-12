package com.pulsegate.service;

import com.pulsegate.entity.RequestEvent;
import com.pulsegate.repository.RequestEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Persists request events for analytics. The rate-limit decision is already made
 * before this runs, so a logging failure never changes a response and never
 * lets a request bypass its limit.
 */
@Component
public class RequestEventRecorder {

    private static final Logger log = LoggerFactory.getLogger(RequestEventRecorder.class);

    private final RequestEventRepository repository;

    public RequestEventRecorder(RequestEventRepository repository) {
        this.repository = repository;
    }

    public void record(RequestEvent event) {
        try {
            repository.save(event);
        } catch (RuntimeException e) {
            log.warn("Failed to record request event (client={}, decision={}): {}",
                    event.getClientId(), event.getDecision(), e.getMessage());
        }
    }
}
