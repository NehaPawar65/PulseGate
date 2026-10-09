// ClientCreatedResponse.java: the only response that ever contains the raw key
package com.pulsegate.dto;

public record ClientCreatedResponse(ClientResponse client, String apiKey) {}
