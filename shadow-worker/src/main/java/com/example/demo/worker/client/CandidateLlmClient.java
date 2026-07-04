package com.example.demo.worker.client;

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
 * Calls the candidate LLM endpoint in the background, off the customer request path.
 *
 * Builds the OpenAI-compatible request body itself from the configured candidate model
 * and the same user text the primary LLM received, so both sides are compared on an
 * apples-to-apples prompt.
 */
@Component
public class CandidateLlmClient {

    private static final Logger log = LoggerFactory.getLogger(CandidateLlmClient.class);

    private final RestClient restClient;
    private final ShadowProxyProperties properties;

    public CandidateLlmClient(ShadowProxyProperties properties) {
        this.properties = properties;
        this.restClient = buildRestClient(properties.getCandidateLlm());
    }

    public CandidateLlmResult call(String userText) {
        long start = System.currentTimeMillis();
        ChatCompletionRequest requestBody = ChatCompletionRequest.of(properties.getCandidateLlm().getDefaultModel(), userText);
        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(properties.getCandidateLlm().getBaseUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .toEntity(String.class);
            long latencyMs = System.currentTimeMillis() - start;
            return new CandidateLlmResult(response.getStatusCode().value(), response.getBody(), latencyMs, null);
        } catch (RestClientResponseException httpError) {
            long latencyMs = System.currentTimeMillis() - start;
            return new CandidateLlmResult(httpError.getStatusCode().value(), httpError.getResponseBodyAsString(),
                    latencyMs, httpError.getMessage());
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - start;
            return new CandidateLlmResult(0, null, latencyMs, e.getMessage());
        }
    }

    private RestClient buildRestClient(ShadowProxyProperties.LlmEndpoint config) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(config.getConnectTimeoutMs());
        requestFactory.setReadTimeout(config.getReadTimeoutMs());

        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        if (config.getAuthToken() != null && !config.getAuthToken().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.getAuthToken());
            log.info("app.candidate-llm.auth-token is configured ({} chars) - Authorization header will be sent to {}.",
                    config.getAuthToken().length(), config.getBaseUrl());
        } else {
            log.warn("app.candidate-llm.auth-token is blank/unset - calls to {} will be sent without an " +
                    "Authorization header and will likely be rejected with 401 by the upstream LLM provider.",
                    config.getBaseUrl());
        }
        return builder.build();
    }
}
