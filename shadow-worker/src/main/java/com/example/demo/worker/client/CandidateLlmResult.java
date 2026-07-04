package com.example.demo.worker.client;

public final class CandidateLlmResult {

    private final int statusCode;
    private final String responseBody;
    private final long latencyMs;
    private final String errorMessage;

    public CandidateLlmResult(int statusCode, String responseBody, long latencyMs, String errorMessage) {
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.latencyMs = latencyMs;
        this.errorMessage = errorMessage;
    }

    public boolean isSuccess() {
        return errorMessage == null;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
