package com.example.demo.proxy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request contract for POST /v1/proxy.
 *
 * The caller supplies plain text only. The proxy builds the OpenAI-compatible chat
 * completion body (model + messages) itself before calling the primary/candidate LLM
 * endpoints, so callers can't:
 * - choose which model handles the request (server-side config, see {@code ShadowProxyProperties}), or
 * - shape the raw request body sent upstream (e.g. inject extra fields, a system prompt, etc).
 *
 * {@code ignoreUnknown = false} makes this explicit and enforced: a request containing
 * e.g. {@code "primaryModel"} or {@code "payload"} is rejected with a 400 instead of
 * silently dropping the field (Jackson's default behavior in Spring Boot is to ignore
 * unknown properties, which would let a caller believe an override "worked").
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public class ProxyRequestDto {

    private String correlationId;
    private String text;

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
