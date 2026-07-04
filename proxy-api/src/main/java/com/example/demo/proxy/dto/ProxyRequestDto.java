package com.example.demo.proxy.dto;

import com.fasterxml.jackson.databind.JsonNode;

public class ProxyRequestDto {

    private String correlationId;
    private JsonNode payload;
    private String primaryModel;
    private String candidateModel;

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }

    public String getPrimaryModel() {
        return primaryModel;
    }

    public void setPrimaryModel(String primaryModel) {
        this.primaryModel = primaryModel;
    }

    public String getCandidateModel() {
        return candidateModel;
    }

    public void setCandidateModel(String candidateModel) {
        this.candidateModel = candidateModel;
    }
}
