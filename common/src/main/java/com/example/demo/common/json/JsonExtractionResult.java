package com.example.demo.common.json;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Result of attempting to extract structured JSON from a raw LLM text response.
 */
public final class JsonExtractionResult {

    private final boolean success;
    private final String rawText;
    private final JsonNode parsedJson;
    private final String errorType;
    private final String errorMessage;

    private JsonExtractionResult(boolean success, String rawText, JsonNode parsedJson,
                                  String errorType, String errorMessage) {
        this.success = success;
        this.rawText = rawText;
        this.parsedJson = parsedJson;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
    }

    public static JsonExtractionResult success(String rawText, JsonNode parsedJson) {
        return new JsonExtractionResult(true, rawText, parsedJson, null, null);
    }

    public static JsonExtractionResult failure(String rawText, String errorType, String errorMessage) {
        return new JsonExtractionResult(false, rawText, null, errorType, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getRawText() {
        return rawText;
    }

    public JsonNode getParsedJson() {
        return parsedJson;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
