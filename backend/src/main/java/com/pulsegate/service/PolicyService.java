// PolicyService.java
package com.pulsegate.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulsegate.dto.CreatePolicyRequest;
import com.pulsegate.dto.PolicyResponse;
import com.pulsegate.entity.Algorithm;
import com.pulsegate.entity.RateLimitPolicy;
import com.pulsegate.exception.ConflictException;
import com.pulsegate.exception.NotFoundException;
import com.pulsegate.repository.RateLimitPolicyRepository;

@Service
public class PolicyService {

    private final RateLimitPolicyRepository repository;

    public PolicyService(RateLimitPolicyRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PolicyResponse create(CreatePolicyRequest req) {
        double refill = req.refillTokensPerSecond() == null ? 0 : req.refillTokensPerSecond();
        if (req.algorithm() == Algorithm.TOKEN_BUCKET && refill <= 0) {
            throw new IllegalArgumentException("refillTokensPerSecond must be > 0 for TOKEN_BUCKET");
        }
        if (req.algorithm() == Algorithm.FIXED_WINDOW && req.windowSeconds() == null) {
            throw new IllegalArgumentException("windowSeconds is required for FIXED_WINDOW");
        }
        if (repository.existsByName(req.name())) {
            throw new ConflictException("Policy '" + req.name() + "' already exists");
        }
        RateLimitPolicy saved = repository.save(new RateLimitPolicy(
                req.name(), req.algorithm(), req.capacity(), refill, req.windowSeconds()));
        return PolicyResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PolicyResponse get(Long id) {
        return repository.findById(id)
                .map(PolicyResponse::from)
                .orElseThrow(() -> new NotFoundException("Policy " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public List<PolicyResponse> list() {
        return repository.findAll().stream().map(PolicyResponse::from).toList();
    }
}
