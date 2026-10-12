package com.pulsegate.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SimulationRequest(
        @NotBlank String apiKey,
        @NotNull @Min(1) @Max(5_000) Integer totalRequests,
        @NotNull @Min(1) @Max(100) Integer concurrency) {}
