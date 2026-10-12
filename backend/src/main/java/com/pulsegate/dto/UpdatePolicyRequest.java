package com.pulsegate.dto;

import com.pulsegate.entity.Algorithm;
import jakarta.validation.constraints.*;

/** Same limits as CreatePolicyRequest; the name is fixed after creation. */
public record UpdatePolicyRequest(
        @NotNull Algorithm algorithm,
        @NotNull @Min(1) @Max(100_000) Integer capacity,
        @PositiveOrZero @DecimalMax("100000") Double refillTokensPerSecond,
        @Positive @Max(86_400) Integer windowSeconds) {}
