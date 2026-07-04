package com.example.demo.proxy.dto;

import com.fasterxml.jackson.databind.JsonNode;

public class ProxyResponseDto {

    private String correlationId;
    private JsonNode primaryResponse;
    private Integer primaryStatusCode;
    private Long primaryLatencyMs;
    private boolean shadowEnqueued;

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public JsonNode getPrimaryResponse() {
        return primaryResponse;
    }

    public void setPrimaryResponse(JsonNode primaryResponse) {
        this.primaryResponse = primaryResponse;
    }

    public Integer getPrimaryStatusCode() {
        return primaryStatusCode;
    }

    public void setPrimaryStatusCode(Integer primaryStatusCode) {
        this.primaryStatusCode = primaryStatusCode;
    }

    public Long getPrimaryLatencyMs() {
        return primaryLatencyMs;
    }

    public void setPrimaryLatencyMs(Long primaryLatencyMs) {
        this.primaryLatencyMs = primaryLatencyMs;
    }

    public boolean isShadowEnqueued() {
        return shadowEnqueued;
    }

    public void setShadowEnqueued(boolean shadowEnqueued) {
        this.shadowEnqueued = shadowEnqueued;
    }
}
