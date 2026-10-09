// PolicyResponse.java
package com.pulsegate.dto;

import java.time.Instant;

import com.pulsegate.entity.Algorithm;
import com.pulsegate.entity.RateLimitPolicy;

public record PolicyResponse(Long id, String name, Algorithm algorithm, int capacity,
                             double refillTokensPerSecond, Integer windowSeconds, Instant createdAt) {

    public static PolicyResponse from(RateLimitPolicy p) {
        return new PolicyResponse(p.getId(), p.getName(), p.getAlgorithm(), p.getCapacity(),
                p.getRefillTokensPerSecond(), p.getWindowSeconds(), p.getCreatedAt());
    }
}
