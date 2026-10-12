// ClientController.java
package com.pulsegate.controller;

import com.pulsegate.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.pulsegate.dto.ClientCreatedResponse;
import com.pulsegate.dto.ClientResponse;
import com.pulsegate.dto.CreateClientRequest;
import com.pulsegate.service.ClientService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@SecurityRequirement(name = OpenApiConfig.ADMIN_SCHEME)
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientCreatedResponse register(@Valid @RequestBody CreateClientRequest request) {
        return clientService.register(request);
    }

    @GetMapping
    public List<ClientResponse> list() {
        return clientService.list();
    }
}
