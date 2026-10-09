// ClientService.java
package com.pulsegate.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulsegate.dto.ClientCreatedResponse;
import com.pulsegate.dto.ClientResponse;
import com.pulsegate.dto.CreateClientRequest;
import com.pulsegate.entity.Client;
import com.pulsegate.entity.RateLimitPolicy;
import com.pulsegate.exception.ConflictException;
import com.pulsegate.exception.NotFoundException;
import com.pulsegate.repository.ClientRepository;
import com.pulsegate.repository.RateLimitPolicyRepository;

@Service
public class ClientService {

    private final ClientRepository clientRepository;
    private final RateLimitPolicyRepository policyRepository;
    private final ApiKeyService apiKeyService;

    public ClientService(ClientRepository clientRepository, RateLimitPolicyRepository policyRepository,
                         ApiKeyService apiKeyService) {
        this.clientRepository = clientRepository;
        this.policyRepository = policyRepository;
        this.apiKeyService = apiKeyService;
    }

    @Transactional
    public ClientCreatedResponse register(CreateClientRequest req) {
        if (clientRepository.existsByName(req.name())) {
            throw new ConflictException("Client '" + req.name() + "' already exists");
        }
        RateLimitPolicy policy = policyRepository.findById(req.policyId())
                .orElseThrow(() -> new NotFoundException("Policy " + req.policyId() + " not found"));

        String apiKey = apiKeyService.generate();
        Client client = clientRepository.save(new Client(
                req.name(), apiKeyService.hash(apiKey), apiKeyService.displayPrefix(apiKey), policy));

        return new ClientCreatedResponse(ClientResponse.from(client), apiKey);
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> list() {
        return clientRepository.findAllByOrderByIdAsc().stream().map(ClientResponse::from).toList();
    }
}
