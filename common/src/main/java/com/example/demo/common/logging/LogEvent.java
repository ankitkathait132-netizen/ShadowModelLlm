package com.example.demo.common.logging;

/**
 * A single structured log entry emitted via {@link StructuredLogger}.
 * Build with {@code LogEvent.builder("shadow.enqueued")...build()}.
 */
public final class LogEvent {

    private final String event;
    private final String component;
    private final String operation;
    private final String correlationId;
    private final String shadowTaskId;
    private final Integer attempt;
    private final String status;
    private final Long durationMs;
    private final String primaryModel;
    private final String candidateModel;
    private final String errorType;
    private final String message;

    private LogEvent(Builder builder) {
        this.event = builder.event;
        this.component = builder.component;
        this.operation = builder.operation;
        this.correlationId = builder.correlationId;
        this.shadowTaskId = builder.shadowTaskId;
        this.attempt = builder.attempt;
        this.status = builder.status;
        this.durationMs = builder.durationMs;
        this.primaryModel = builder.primaryModel;
        this.candidateModel = builder.candidateModel;
        this.errorType = builder.errorType;
        this.message = builder.message;
    }

    public static Builder builder(String event) {
        return new Builder(event);
    }

    public String getEvent() {
        return event;
    }

    public String getComponent() {
        return component;
    }

    public String getOperation() {
        return operation;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getShadowTaskId() {
        return shadowTaskId;
    }

    public Integer getAttempt() {
        return attempt;
    }

    public String getStatus() {
        return status;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public String getPrimaryModel() {
        return primaryModel;
    }

    public String getCandidateModel() {
        return candidateModel;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getMessage() {
        return message;
    }

    public static final class Builder {
        private final String event;
        private String component;
        private String operation;
        private String correlationId;
        private String shadowTaskId;
        private Integer attempt;
        private String status;
        private Long durationMs;
        private String primaryModel;
        private String candidateModel;
        private String errorType;
        private String message;

        private Builder(String event) {
            this.event = event;
        }

        public Builder component(String component) {
            this.component = component;
            return this;
        }

        public Builder operation(String operation) {
            this.operation = operation;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder shadowTaskId(String shadowTaskId) {
            this.shadowTaskId = shadowTaskId;
            return this;
        }

        public Builder attempt(Integer attempt) {
            this.attempt = attempt;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder durationMs(Long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder primaryModel(String primaryModel) {
            this.primaryModel = primaryModel;
            return this;
        }

        public Builder candidateModel(String candidateModel) {
            this.candidateModel = candidateModel;
            return this;
        }

        public Builder errorType(String errorType) {
            this.errorType = errorType;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public LogEvent build() {
            return new LogEvent(this);
        }
    }
}
