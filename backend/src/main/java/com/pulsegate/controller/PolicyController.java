// PolicyController.java
package com.pulsegate.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pulsegate.dto.CreatePolicyRequest;
import com.pulsegate.dto.PolicyResponse;
import com.pulsegate.service.PolicyService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PolicyResponse create(@Valid @RequestBody CreatePolicyRequest request) {
        return policyService.create(request);
    }

    @GetMapping
    public List<PolicyResponse> list() {
        return policyService.list();
    }

    @GetMapping("/{id}")
    public PolicyResponse get(@PathVariable Long id) {
        return policyService.get(id);
    }
}
