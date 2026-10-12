package com.pulsegate.controller;

import com.pulsegate.dto.EventResponse;
import com.pulsegate.service.EventService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    /** Most recent decisions first. limit is clamped to 1..500. */
    @GetMapping
    public List<EventResponse> recent(@RequestParam(defaultValue = "50") int limit,
                                      @RequestParam(required = false) Long clientId) {
        return eventService.recent(limit, clientId);
    }

    /** Clears event history (e.g. before a clean demo run). Quotas are unaffected. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear() {
        eventService.deleteAll();
    }
}
