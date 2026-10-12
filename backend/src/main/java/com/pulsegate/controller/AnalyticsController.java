package com.pulsegate.controller;

import com.pulsegate.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.pulsegate.dto.AnalyticsSummary;
import com.pulsegate.service.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = OpenApiConfig.ADMIN_SCHEME)
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    public AnalyticsSummary summary() {
        return analyticsService.summary();
    }
}
