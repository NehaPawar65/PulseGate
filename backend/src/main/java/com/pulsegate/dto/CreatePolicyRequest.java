// CreatePolicyRequest.java
package com.pulsegate.dto;

import com.pulsegate.entity.Algorithm;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreatePolicyRequest(
        @NotBlank @Size(max = 50) String name,
        @NotNull Algorithm algorithm,
        @NotNull @Min(1) @Max(100_000) Integer capacity,
        @PositiveOrZero @DecimalMax("100000") Double refillTokensPerSecond,
        @Positive @Max(86_400) Integer windowSeconds) {}
