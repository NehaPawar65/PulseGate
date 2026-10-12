package com.pulsegate.service;

import com.pulsegate.dto.EventResponse;
import com.pulsegate.entity.Client;
import com.pulsegate.entity.RequestEvent;
import com.pulsegate.repository.ClientRepository;
import com.pulsegate.repository.RequestEventRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    static final int MAX_LIMIT = 500;

    private final RequestEventRepository eventRepository;
    private final ClientRepository clientRepository;

    public EventService(RequestEventRepository eventRepository, ClientRepository clientRepository) {
        this.eventRepository = eventRepository;
        this.clientRepository = clientRepository;
    }

    /** Most recent events first, optionally for one client. */
    @Transactional(readOnly = true)
    public List<EventResponse> recent(int limit, Long clientId) {
        PageRequest page = PageRequest.of(0, Math.clamp(limit, 1, MAX_LIMIT));
        List<RequestEvent> events = clientId == null
                ? eventRepository.findAllByOrderByIdDesc(page)
                : eventRepository.findByClientIdOrderByIdDesc(clientId, page);

        Map<Long, String> names = clientNames(events.stream().map(RequestEvent::getClientId)
                .filter(Objects::nonNull).distinct().toList());
        return events.stream()
                .map(e -> EventResponse.from(e, e.getClientId() == null ? null : names.get(e.getClientId())))
                .toList();
    }

    @Transactional
    public void deleteAll() {
        eventRepository.deleteAllInBatch();
    }

    // One query for all names instead of one per event.
    private Map<Long, String> clientNames(List<Long> ids) {
        return clientRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Client::getId, Client::getName, (a, b) -> a));
    }
}
