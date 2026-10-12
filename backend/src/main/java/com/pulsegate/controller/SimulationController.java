package com.pulsegate.controller;

import com.pulsegate.dto.SimulationRequest;
import com.pulsegate.dto.SimulationResult;
import com.pulsegate.service.SimulationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulations")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    /**
     * Runs synchronously and returns when every request has completed.
     * The target is always this server's own protected endpoint, never a caller-supplied URL.
     */
    @PostMapping
    public SimulationResult run(@Valid @RequestBody SimulationRequest request, HttpServletRequest http) {
        URI target = URI.create("http://localhost:" + http.getLocalPort() + "/api/protected/resource");
        return simulationService.run(request, target);
    }
}
