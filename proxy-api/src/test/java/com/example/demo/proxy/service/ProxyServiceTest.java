package com.example.demo.proxy.service;

import com.example.demo.common.config.ShadowProxyProperties;
import com.example.demo.common.dto.ShadowTaskDto;
import com.example.demo.common.logging.StructuredLogger;
import com.example.demo.proxy.client.PrimaryLlmClient;
import com.example.demo.proxy.client.PrimaryLlmResult;
import com.example.demo.proxy.dto.ProxyRequestDto;
import com.example.demo.proxy.dto.ProxyResponseDto;
import com.example.demo.proxy.kafka.ShadowTaskProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProxyServiceTest {

    private PrimaryLlmClient primaryLlmClient;
    private ShadowTaskProducer shadowTaskProducer;
    private ShadowProxyProperties properties;
    private StructuredLogger structuredLogger;
    private ProxyService proxyService;

    @BeforeEach
    void setUp() {
        primaryLlmClient = Mockito.mock(PrimaryLlmClient.class);
        shadowTaskProducer = Mockito.mock(ShadowTaskProducer.class);
        structuredLogger = Mockito.mock(StructuredLogger.class);

        properties = new ShadowProxyProperties();
        properties.getPrimaryLlm().setDefaultModel("primary-model");
        properties.getCandidateLlm().setDefaultModel("candidate-model");
        properties.getShadow().setEnabled(true);

        proxyService = new ProxyService(primaryLlmClient, shadowTaskProducer, properties,
                structuredLogger, new ObjectMapper());
    }

    @Test
    void throwsBadRequestWhenTextIsNull() {
        ProxyRequestDto request = new ProxyRequestDto();
        request.setText(null);

        assertThatThrownBy(() -> proxyService.handleProxyRequest(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("text is required");

        verify(primaryLlmClient, never()).call(anyString());
    }

    @Test
    void throwsBadRequestWhenTextIsBlank() {
        ProxyRequestDto request = new ProxyRequestDto();
        request.setText("   ");

        assertThatThrownBy(() -> proxyService.handleProxyRequest(request))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void callsPrimaryLlmWithRequestTextAndReturnsParsedJsonResponse() {
        ProxyRequestDto request = new ProxyRequestDto();
        request.setText("What is the capital of France?");
        request.setCorrelationId("corr-1");

        when(primaryLlmClient.call("What is the capital of France?"))
                .thenReturn(new PrimaryLlmResult(200, "{\"answer\":\"Paris\"}", 120L, null));

        ProxyResponseDto response = proxyService.handleProxyRequest(request);

        assertThat(response.getCorrelationId()).isEqualTo("corr-1");
        assertThat(response.getPrimaryStatusCode()).isEqualTo(200);
        assertThat(response.getPrimaryLatencyMs()).isEqualTo(120L);
        assertThat(response.getPrimaryResponse().get("answer").asText()).isEqualTo("Paris");
        assertThat(response.isShadowEnqueued()).isTrue();

        verify(primaryLlmClient).call("What is the capital of France?");
    }

    @Test
    void generatesCorrelationIdWhenNotProvided() {
        ProxyRequestDto request = new ProxyRequestDto();
        request.setText("hello");
        request.setCorrelationId(null);

        when(primaryLlmClient.call(anyString())).thenReturn(new PrimaryLlmResult(200, "{}", 10L, null));

        ProxyResponseDto response = proxyService.handleProxyRequest(request);

        assertThat(response.getCorrelationId()).isNotNull();
        assertThat(response.getCorrelationId()).isNotBlank();
    }

    @Test
    void generatesCorrelationIdWhenBlank() {
        ProxyRequestDto request = new ProxyRequestDto();
        request.setText("hello");
        request.setCorrelationId("   ");

        when(primaryLlmClient.call(anyString())).thenReturn(new PrimaryLlmResult(200, "{}", 10L, null));

        ProxyResponseDto response = proxyService.handleProxyRequest(request);

        assertThat(response.getCorrelationId()).isNotBlank();
        assertThat(response.getCorrelationId()).isNotEqualTo("   ");
    }

    @Test
    void wrapsNonJsonPrimaryResponseAsTextNode() {
        ProxyRequestDto request = new ProxyRequestDto();
        request.setText("hello");

        when(primaryLlmClient.call(anyString())).thenReturn(new PrimaryLlmResult(200, "plain text response", 10L, null));

        ProxyResponseDto response = proxyService.handleProxyRequest(request);

        assertThat(response.getPrimaryResponse().asText()).isEqualTo("plain text response");
    }

    @Test
    void primaryResponseIsNullWhenResponseBodyIsNull() {
        ProxyRequestDto request = new ProxyRequestDto();
        request.setText("hello");

        when(primaryLlmClient.call(anyString())).thenReturn(new PrimaryLlmResult(401, null, 5L, "unauthorized"));

        ProxyResponseDto response = proxyService.handleProxyRequest(request);

        assertThat(response.getPrimaryResponse()).isNull();
        assertThat(response.getPrimaryStatusCode()).isEqualTo(401);
    }

    @Test
    void publishesShadowTaskWithModelsFromPropertiesNotFromRequest() {
        ProxyRequestDto request = new ProxyRequestDto();
        request.setText("hello world");
        request.setCorrelationId("corr-42");

        when(primaryLlmClient.call("hello world")).thenReturn(new PrimaryLlmResult(200, "{\"a\":1}", 42L, null));

        proxyService.handleProxyRequest(request);

        ArgumentCaptor<ShadowTaskDto> captor = ArgumentCaptor.forClass(ShadowTaskDto.class);
        verify(shadowTaskProducer, times(1)).publish(captor.capture());

        ShadowTaskDto task = captor.getValue();
        assertThat(task.getCorrelationId()).isEqualTo("corr-42");
        assertThat(task.getRequestText()).isEqualTo("hello world");
        assertThat(task.getRequestPayloadRedacted()).isEqualTo("hello world");
        assertThat(task.getPrimaryModel()).isEqualTo("primary-model");
        assertThat(task.getCandidateModel()).isEqualTo("candidate-model");
        assertThat(task.getPrimaryStatusCode()).isEqualTo(200);
        assertThat(task.getPrimaryLatencyMs()).isEqualTo(42L);
        assertThat(task.getAttempt()).isEqualTo(1);
        assertThat(task.getMaxAttempts()).isEqualTo(properties.getRetry().getMaxAttempts());
        assertThat(task.getRequestHash()).isNotBlank();
        assertThat(task.getShadowTaskId()).isNotBlank();
        assertThat(task.getFirstAttemptTimestamp()).isNotNull();
    }

    @Test
    void doesNotPublishShadowTaskWhenShadowDisabled() {
        properties.getShadow().setEnabled(false);
        ProxyRequestDto request = new ProxyRequestDto();
        request.setText("hello");

        when(primaryLlmClient.call(anyString())).thenReturn(new PrimaryLlmResult(200, "{}", 10L, null));

        ProxyResponseDto response = proxyService.handleProxyRequest(request);

        assertThat(response.isShadowEnqueued()).isFalse();
        verify(shadowTaskProducer, never()).publish(any());
    }

    @Test
    void truncatesOversizedTextBeforePersistingToShadowTask() {
        properties.getRedaction().setMaxPersistedPayloadSize(10);
        String longText = "0123456789ABCDEFGHIJ";
        ProxyRequestDto request = new ProxyRequestDto();
        request.setText(longText);

        when(primaryLlmClient.call(longText)).thenReturn(new PrimaryLlmResult(200, "{}", 10L, null));

        proxyService.handleProxyRequest(request);

        ArgumentCaptor<ShadowTaskDto> captor = ArgumentCaptor.forClass(ShadowTaskDto.class);
        verify(shadowTaskProducer).publish(captor.capture());

        ShadowTaskDto task = captor.getValue();
        assertThat(task.getRequestText()).isEqualTo(longText);
        assertThat(task.getRequestPayloadRedacted()).isEqualTo("0123456789...(truncated)");
    }
}
