package com.example.demo.worker.client;

import com.example.demo.common.config.ShadowProxyProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Calls the candidate LLM endpoint in the background, off the customer request path.
 */
@Component
public class CandidateLlmClient {

    private final RestClient restClient;
    private final ShadowProxyProperties properties;

    public CandidateLlmClient(ShadowProxyProperties properties) {
        this.properties = properties;
        this.restClient = buildRestClient(properties.getCandidateLlm());
    }

    public CandidateLlmResult call(String requestPayloadJson) {
        long start = System.currentTimeMillis();
        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(properties.getCandidateLlm().getBaseUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestPayloadJson != null ? requestPayloadJson : "{}")
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
        }
        return builder.build();
    }
}
