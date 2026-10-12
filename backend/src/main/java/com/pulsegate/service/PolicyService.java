// PolicyService.java
package com.pulsegate.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulsegate.dto.CreatePolicyRequest;
import com.pulsegate.dto.PolicyResponse;
import com.pulsegate.dto.UpdatePolicyRequest;
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
        double refill = validatedRefill(req.algorithm(), req.refillTokensPerSecond(), req.windowSeconds());
        if (repository.existsByName(req.name())) {
            throw new ConflictException("Policy '" + req.name() + "' already exists");
        }
        RateLimitPolicy saved = repository.save(new RateLimitPolicy(
                req.name(), req.algorithm(), req.capacity(), refill, req.windowSeconds()));
        return PolicyResponse.from(saved);
    }

    /**
     * Clients on this policy pick up the new limits on their next request:
     * RateLimiterService replaces a bucket whose capacity or rate no longer matches.
     */
    @Transactional
    public PolicyResponse update(Long id, UpdatePolicyRequest req) {
        double refill = validatedRefill(req.algorithm(), req.refillTokensPerSecond(), req.windowSeconds());
        RateLimitPolicy policy = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Policy " + id + " not found"));
        policy.setAlgorithm(req.algorithm());
        policy.setCapacity(req.capacity());
        policy.setRefillTokensPerSecond(refill);
        policy.setWindowSeconds(req.windowSeconds());
        return PolicyResponse.from(policy);
    }

    private static double validatedRefill(Algorithm algorithm, Double refillTokensPerSecond, Integer windowSeconds) {
        double refill = refillTokensPerSecond == null ? 0 : refillTokensPerSecond;
        if (algorithm == Algorithm.TOKEN_BUCKET && refill <= 0) {
            throw new IllegalArgumentException("refillTokensPerSecond must be > 0 for TOKEN_BUCKET");
        }
        if (algorithm == Algorithm.FIXED_WINDOW && windowSeconds == null) {
            throw new IllegalArgumentException("windowSeconds is required for FIXED_WINDOW");
        }
        return refill;
    }

    @Transactional(readOnly = true)
    public PolicyResponse get(Long id) {
        return repository.findById(id)
                .map(PolicyResponse::from)
                .orElseThrow(() -> new NotFoundException("Policy " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public List<PolicyResponse> list() {
        return repository.findAll(Sort.by("id")).stream().map(PolicyResponse::from).toList();
    }
}
