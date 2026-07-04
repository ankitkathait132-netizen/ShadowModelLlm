package com.example.demo.proxy.client;

import com.example.demo.common.config.ShadowProxyProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Calls the primary LLM endpoint synchronously on the customer request path.
 */
@Component
public class PrimaryLlmClient {

    private final RestClient restClient;
    private final ShadowProxyProperties properties;

    public PrimaryLlmClient(ShadowProxyProperties properties) {
        this.properties = properties;
        this.restClient = buildRestClient(properties.getPrimaryLlm());
    }

    public PrimaryLlmResult call(Object requestPayload) {
        long start = System.currentTimeMillis();
        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(properties.getPrimaryLlm().getBaseUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestPayload)
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
        }
        return builder.build();
    }
}
