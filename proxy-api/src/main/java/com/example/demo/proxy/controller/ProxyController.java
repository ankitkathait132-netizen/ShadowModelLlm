package com.example.demo.proxy.controller;

import com.example.demo.proxy.dto.ProxyRequestDto;
import com.example.demo.proxy.dto.ProxyResponseDto;
import com.example.demo.proxy.service.ProxyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/proxy")
public class ProxyController {

    private final ProxyService proxyService;

    public ProxyController(ProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @PostMapping
    public ResponseEntity<ProxyResponseDto> proxy(@RequestBody ProxyRequestDto request) {
        return ResponseEntity.ok(proxyService.handleProxyRequest(request));
    }
}
