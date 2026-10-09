package com.pulsegate.repository;

import com.pulsegate.entity.RateLimitPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RateLimitPolicyRepository extends JpaRepository<RateLimitPolicy, Long> {
    boolean existsByName(String name);
}
