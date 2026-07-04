package com.example.demo.proxy.client;

import com.example.demo.common.config.ShadowProxyProperties;
import com.example.demo.common.llm.ChatCompletionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Calls the primary LLM endpoint synchronously on the customer request path.
 *
 * Builds the OpenAI-compatible request body itself from the configured model and the
 * caller's plain text; the model is never taken from the caller.
 */
@Component
public class PrimaryLlmClient {

    private static final Logger log = LoggerFactory.getLogger(PrimaryLlmClient.class);

    private final RestClient restClient;
    private final ShadowProxyProperties properties;

    public PrimaryLlmClient(ShadowProxyProperties properties) {
        this.properties = properties;
        this.restClient = buildRestClient(properties.getPrimaryLlm());
    }

    public PrimaryLlmResult call(String userText) {
        long start = System.currentTimeMillis();
        ChatCompletionRequest requestBody = ChatCompletionRequest.of(properties.getPrimaryLlm().getDefaultModel(), userText);
        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(properties.getPrimaryLlm().getBaseUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .toEntity(String.class);
            long latencyMs = System.currentTimeMillis() - start;
            return new PrimaryLlmResult(response.getStatusCode().value(), response.getBody(), latencyMs, null);
        } catch (RestClientResponseException httpError) {
            long latencyMs = System.currentTimeMillis() - start;
            return new PrimaryLlmResult(httpError.getStatusCode().value(), httpError.getResponseBodyAsString(),
                    latencyMs, httpError.getMessage());
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - start;
            return new PrimaryLlmResult(0, null, latencyMs, e.getMessage());
        }
    }

    private RestClient buildRestClient(ShadowProxyProperties.LlmEndpoint config) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(config.getConnectTimeoutMs());
        requestFactory.setReadTimeout(config.getReadTimeoutMs());

        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        if (config.getAuthToken() != null && !config.getAuthToken().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.getAuthToken());
            log.info("app.primary-llm.auth-token is configured ({} chars) - Authorization header will be sent to {}.",
                    config.getAuthToken().length(), config.getBaseUrl());
        } else {
            log.warn("app.primary-llm.auth-token is blank/unset - calls to {} will be sent without an " +
                    "Authorization header and will likely be rejected with 401 by the upstream LLM provider.",
                    config.getBaseUrl());
        }
        return builder.build();
    }
}
