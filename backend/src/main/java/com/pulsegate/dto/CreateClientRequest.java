// CreateClientRequest.java
package com.pulsegate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateClientRequest(@NotBlank @Size(max = 100) String name, @NotNull Long policyId) {}
