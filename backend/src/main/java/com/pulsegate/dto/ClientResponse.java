// ClientResponse.java
package com.pulsegate.dto;

import java.time.Instant;

import com.pulsegate.entity.Client;

public record ClientResponse(Long id, String name, String apiKeyPrefix, boolean active,
                             Long policyId, String policyName, Instant createdAt) {

    public static ClientResponse from(Client c) {
        return new ClientResponse(c.getId(), c.getName(), c.getApiKeyPrefix(), c.isActive(),
                c.getPolicy().getId(), c.getPolicy().getName(), c.getCreatedAt());
    }
}
