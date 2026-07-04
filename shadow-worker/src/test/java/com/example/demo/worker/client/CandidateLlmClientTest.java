package com.example.demo.worker.client;

import com.example.demo.common.config.ShadowProxyProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateLlmClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private ShadowProxyProperties propertiesFor(String baseUrl, String authToken) {
        ShadowProxyProperties properties = new ShadowProxyProperties();
        properties.getCandidateLlm().setBaseUrl(baseUrl);
        properties.getCandidateLlm().setAuthToken(authToken);
        properties.getCandidateLlm().setDefaultModel("mistral-7b-instruct-v0.3");
        properties.getCandidateLlm().setConnectTimeoutMs(1000);
        properties.getCandidateLlm().setReadTimeoutMs(2000);
        return properties;
    }

    private HttpServer startServer(int statusCode, String responseBody, StringBuilder capturedAuthHeader,
                                    StringBuilder capturedRequestBody) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        httpServer.createContext("/chat/completions", exchange -> {
            if (capturedAuthHeader != null) {
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                capturedAuthHeader.append(authHeader == null ? "" : authHeader);
            }
            if (capturedRequestBody != null) {
                capturedRequestBody.append(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        httpServer.start();
        return httpServer;
    }

    private String baseUrl(HttpServer httpServer) {
        return "http://localhost:" + httpServer.getAddress().getPort() + "/chat/completions";
    }

    @Test
    void returnsSuccessfulResultForOkResponse() throws Exception {
        server = startServer(200, "{\"choices\":[{\"message\":{\"content\":\"Paris\"}}]}", null, null);
        CandidateLlmClient client = new CandidateLlmClient(propertiesFor(baseUrl(server), "test-token"));

        CandidateLlmResult result = client.call("What is the capital of France?");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStatusCode()).isEqualTo(200);
        assertThat(result.getResponseBody()).contains("Paris");
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    void sendsAuthorizationHeaderWhenTokenConfigured() throws Exception {
        StringBuilder capturedAuthHeader = new StringBuilder();
        server = startServer(200, "{}", capturedAuthHeader, null);
        CandidateLlmClient client = new CandidateLlmClient(propertiesFor(baseUrl(server), "my-secret-token"));

        client.call("hello");

        assertThat(capturedAuthHeader.toString()).isEqualTo("Bearer my-secret-token");
    }

    @Test
    void omitsAuthorizationHeaderWhenTokenBlank() throws Exception {
        StringBuilder capturedAuthHeader = new StringBuilder();
        server = startServer(200, "{}", capturedAuthHeader, null);
        CandidateLlmClient client = new CandidateLlmClient(propertiesFor(baseUrl(server), ""));

        client.call("hello");

        assertThat(capturedAuthHeader.toString()).isEmpty();
    }

    @Test
    void sendsModelFromConfigurationAndFullUserTextInRequestBody() throws Exception {
        StringBuilder capturedRequestBody = new StringBuilder();
        server = startServer(200, "{}", null, capturedRequestBody);
        CandidateLlmClient client = new CandidateLlmClient(propertiesFor(baseUrl(server), "token"));

        client.call("the full untruncated user text");

        assertThat(capturedRequestBody.toString()).contains("\"model\":\"mistral-7b-instruct-v0.3\"");
        assertThat(capturedRequestBody.toString()).contains("\"content\":\"the full untruncated user text\"");
    }

    @Test
    void returnsFailureResultForHttpErrorStatus() throws Exception {
        server = startServer(500, "{\"error\":\"internal\"}", null, null);
        CandidateLlmClient client = new CandidateLlmClient(propertiesFor(baseUrl(server), "token"));

        CandidateLlmResult result = client.call("hello");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatusCode()).isEqualTo(500);
        assertThat(result.getErrorMessage()).isNotNull();
    }

    @Test
    void returnsFailureResultWhenConnectionRefused() {
        ShadowProxyProperties properties = propertiesFor("http://localhost:1/chat/completions", "token");
        CandidateLlmClient client = new CandidateLlmClient(properties);

        CandidateLlmResult result = client.call("hello");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatusCode()).isEqualTo(0);
        assertThat(result.getErrorMessage()).isNotNull();
    }
}
