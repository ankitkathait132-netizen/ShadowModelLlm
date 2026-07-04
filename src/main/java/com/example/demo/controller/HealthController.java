package com.example.demo.controller;

import com.example.demo.dto.HealthOutputDto;
import com.example.demo.service.HealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping
    public ResponseEntity<HealthOutputDto> health() {
        return ResponseEntity.ok(healthService.checkHealth());
    }
}
