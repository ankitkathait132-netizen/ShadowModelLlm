package com.example.demo.proxy.service;

import com.example.demo.common.config.ShadowProxyProperties;
import com.example.demo.common.dto.ShadowTaskDto;
import com.example.demo.common.logging.LogEvent;
import com.example.demo.common.logging.StructuredLogger;
import com.example.demo.common.util.RequestHasher;
import com.example.demo.proxy.client.PrimaryLlmClient;
import com.example.demo.proxy.client.PrimaryLlmResult;
import com.example.demo.proxy.dto.ProxyRequestDto;
import com.example.demo.proxy.dto.ProxyResponseDto;
import com.example.demo.proxy.kafka.ShadowTaskProducer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

/**
 * Orchestrates the customer-facing proxy request flow: call the primary LLM
 * synchronously, publish a shadow task to Kafka in the background, and return
 * the primary response immediately.
 */
@Service
public class ProxyService {

    private static final String COMPONENT = "proxy-api";

    private final PrimaryLlmClient primaryLlmClient;
    private final ShadowTaskProducer shadowTaskProducer;
    private final ShadowProxyProperties properties;
    private final StructuredLogger structuredLogger;
    private final ObjectMapper objectMapper;

    public ProxyService(PrimaryLlmClient primaryLlmClient, ShadowTaskProducer shadowTaskProducer,
                         ShadowProxyProperties properties, StructuredLogger structuredLogger,
                         ObjectMapper objectMapper) {
        this.primaryLlmClient = primaryLlmClient;
        this.shadowTaskProducer = shadowTaskProducer;
        this.properties = properties;
        this.structuredLogger = structuredLogger;
        this.objectMapper = objectMapper;
    }

    public ProxyResponseDto handleProxyRequest(ProxyRequestDto request) {
        if (request.getText() == null || request.getText().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text is required");
        }

        String correlationId = firstNonBlank(request.getCorrelationId(), UUID.randomUUID().toString());

        structuredLogger.log(LogEvent.builder("proxy.request.received")
                .component(COMPONENT)
                .operation("handle_request")
                .correlationId(correlationId)
                .status("received")
                .build());

        String primaryModel = properties.getPrimaryLlm().getDefaultModel();
        String candidateModel = properties.getCandidateLlm().getDefaultModel();

        PrimaryLlmResult primaryResult = primaryLlmClient.call(request.getText());

        structuredLogger.log(LogEvent.builder("proxy.primary.completed")
                .component(COMPONENT)
                .operation("primary_call")
                .correlationId(correlationId)
                .status(primaryResult.isSuccess() ? "success" : "failure")
                .durationMs(primaryResult.getLatencyMs())
                .primaryModel(primaryModel)
                .errorType(primaryResult.isSuccess() ? null : "primary_call_error")
                .message(primaryResult.getErrorMessage())
                .build());

        boolean shadowEnqueued = false;
        if (properties.getShadow().isEnabled()) {
            ShadowTaskDto task = buildShadowTask(request, correlationId, primaryResult, primaryModel, candidateModel);
            shadowTaskProducer.publish(task);
            shadowEnqueued = true;
        }

        ProxyResponseDto response = new ProxyResponseDto();
        response.setCorrelationId(correlationId);
        response.setPrimaryResponse(parseResponseSafely(primaryResult.getResponseBody()));
        response.setPrimaryStatusCode(primaryResult.getStatusCode());
        response.setPrimaryLatencyMs(primaryResult.getLatencyMs());
        response.setShadowEnqueued(shadowEnqueued);
        return response;
    }

    private ShadowTaskDto buildShadowTask(ProxyRequestDto request, String correlationId,
                                           PrimaryLlmResult primaryResult, String primaryModel, String candidateModel) {
        String requestText = request.getText();
        String requestHash = RequestHasher.sha256Hex(requestText);

        return ShadowTaskDto.builder()
                .shadowTaskId(UUID.randomUUID().toString())
                .correlationId(correlationId)
                .requestText(requestText)
                .requestPayloadRedacted(truncateForPersistence(requestText))
                .requestHash(requestHash)
                .primaryResponsePayload(truncateForPersistence(primaryResult.getResponseBody()))
                .primaryStatusCode(primaryResult.getStatusCode())
                .primaryLatencyMs(primaryResult.getLatencyMs())
                .primaryModel(primaryModel)
                .candidateModel(candidateModel)
                .attempt(1)
                .maxAttempts(properties.getRetry().getMaxAttempts())
                .firstAttemptTimestamp(Instant.now())
                .build();
    }

    private String truncateForPersistence(String value) {
        if (value == null) {
            return null;
        }
        int maxSize = properties.getRedaction().getMaxPersistedPayloadSize();
        return value.length() > maxSize ? value.substring(0, maxSize) + "...(truncated)" : value;
    }

    private JsonNode parseResponseSafely(String responseBody) {
        if (responseBody == null) {
            return null;
        }
        try {
            return objectMapper.readTree(responseBody);
        } catch (JsonProcessingException notJson) {
            return new TextNode(responseBody);
        }
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }
}
