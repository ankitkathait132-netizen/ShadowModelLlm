package com.example.demo.proxy.controller;

import com.example.demo.proxy.dto.ProxyRequestDto;
import com.example.demo.proxy.dto.ProxyResponseDto;
import com.example.demo.proxy.service.ProxyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProxyControllerTest {

    private ProxyService proxyService;
    private ProxyController controller;

    @BeforeEach
    void setUp() {
        proxyService = Mockito.mock(ProxyService.class);
        controller = new ProxyController(proxyService);
    }

    @Test
    void delegatesToProxyServiceAndReturnsOkWithBody() {
        ProxyRequestDto request = new ProxyRequestDto();
        request.setText("hello");

        ProxyResponseDto expectedResponse = new ProxyResponseDto();
        expectedResponse.setCorrelationId("corr-1");
        when(proxyService.handleProxyRequest(request)).thenReturn(expectedResponse);

        ResponseEntity<ProxyResponseDto> response = controller.proxy(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expectedResponse);
        verify(proxyService).handleProxyRequest(request);
    }
}
