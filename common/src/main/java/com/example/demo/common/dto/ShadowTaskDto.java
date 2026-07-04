package com.example.demo.common.dto;

import java.time.Instant;

/**
 * Shared shadow task payload published to Kafka by proxy-api and consumed by shadow-worker.
 */
public class ShadowTaskDto {

    private String shadowTaskId;
    private String correlationId;
    private String requestPayloadRedacted;
    private String requestHash;
    private String primaryResponsePayload;
    private Integer primaryStatusCode;
    private Long primaryLatencyMs;
    private String primaryModel;
    private String candidateModel;
    private int attempt;
    private int maxAttempts;
    private Instant firstAttemptTimestamp;
    private String lastErrorType;
    private String lastErrorMessage;

    public ShadowTaskDto() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getShadowTaskId() {
        return shadowTaskId;
    }

    public void setShadowTaskId(String shadowTaskId) {
        this.shadowTaskId = shadowTaskId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getRequestPayloadRedacted() {
        return requestPayloadRedacted;
    }

    public void setRequestPayloadRedacted(String requestPayloadRedacted) {
        this.requestPayloadRedacted = requestPayloadRedacted;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public String getPrimaryResponsePayload() {
        return primaryResponsePayload;
    }

    public void setPrimaryResponsePayload(String primaryResponsePayload) {
        this.primaryResponsePayload = primaryResponsePayload;
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

    public int getAttempt() {
        return attempt;
    }

    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Instant getFirstAttemptTimestamp() {
        return firstAttemptTimestamp;
    }

    public void setFirstAttemptTimestamp(Instant firstAttemptTimestamp) {
        this.firstAttemptTimestamp = firstAttemptTimestamp;
    }

    public String getLastErrorType() {
        return lastErrorType;
    }

    public void setLastErrorType(String lastErrorType) {
        this.lastErrorType = lastErrorType;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public static final class Builder {
        private final ShadowTaskDto instance = new ShadowTaskDto();

        public Builder shadowTaskId(String shadowTaskId) {
            instance.setShadowTaskId(shadowTaskId);
            return this;
        }

        public Builder correlationId(String correlationId) {
            instance.setCorrelationId(correlationId);
            return this;
        }

        public Builder requestPayloadRedacted(String requestPayloadRedacted) {
            instance.setRequestPayloadRedacted(requestPayloadRedacted);
            return this;
        }

        public Builder requestHash(String requestHash) {
            instance.setRequestHash(requestHash);
            return this;
        }

        public Builder primaryResponsePayload(String primaryResponsePayload) {
            instance.setPrimaryResponsePayload(primaryResponsePayload);
            return this;
        }

        public Builder primaryStatusCode(Integer primaryStatusCode) {
            instance.setPrimaryStatusCode(primaryStatusCode);
            return this;
        }

        public Builder primaryLatencyMs(Long primaryLatencyMs) {
            instance.setPrimaryLatencyMs(primaryLatencyMs);
            return this;
        }

        public Builder primaryModel(String primaryModel) {
            instance.setPrimaryModel(primaryModel);
            return this;
        }

        public Builder candidateModel(String candidateModel) {
            instance.setCandidateModel(candidateModel);
            return this;
        }

        public Builder attempt(int attempt) {
            instance.setAttempt(attempt);
            return this;
        }

        public Builder maxAttempts(int maxAttempts) {
            instance.setMaxAttempts(maxAttempts);
            return this;
        }

        public Builder firstAttemptTimestamp(Instant firstAttemptTimestamp) {
            instance.setFirstAttemptTimestamp(firstAttemptTimestamp);
            return this;
        }

        public Builder lastErrorType(String lastErrorType) {
            instance.setLastErrorType(lastErrorType);
            return this;
        }

        public Builder lastErrorMessage(String lastErrorMessage) {
            instance.setLastErrorMessage(lastErrorMessage);
            return this;
        }

        public ShadowTaskDto build() {
            return instance;
        }
    }
}
