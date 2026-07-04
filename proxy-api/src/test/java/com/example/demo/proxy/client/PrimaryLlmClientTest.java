package com.example.demo.proxy.client;

import com.example.demo.common.config.ShadowProxyProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PrimaryLlmClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private ShadowProxyProperties propertiesFor(String baseUrl, String authToken) {
        ShadowProxyProperties properties = new ShadowProxyProperties();
        properties.getPrimaryLlm().setBaseUrl(baseUrl);
        properties.getPrimaryLlm().setAuthToken(authToken);
        properties.getPrimaryLlm().setDefaultModel("llama3.3-70b-instruct");
        properties.getPrimaryLlm().setConnectTimeoutMs(1000);
        properties.getPrimaryLlm().setReadTimeoutMs(2000);
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
        PrimaryLlmClient client = new PrimaryLlmClient(propertiesFor(baseUrl(server), "test-token"));

        PrimaryLlmResult result = client.call("What is the capital of France?");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStatusCode()).isEqualTo(200);
        assertThat(result.getResponseBody()).contains("Paris");
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getLatencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void sendsAuthorizationHeaderWhenTokenConfigured() throws Exception {
        StringBuilder capturedAuthHeader = new StringBuilder();
        server = startServer(200, "{}", capturedAuthHeader, null);
        PrimaryLlmClient client = new PrimaryLlmClient(propertiesFor(baseUrl(server), "my-secret-token"));

        client.call("hello");

        assertThat(capturedAuthHeader.toString()).isEqualTo("Bearer my-secret-token");
    }

    @Test
    void omitsAuthorizationHeaderWhenTokenBlank() throws Exception {
        StringBuilder capturedAuthHeader = new StringBuilder();
        server = startServer(200, "{}", capturedAuthHeader, null);
        PrimaryLlmClient client = new PrimaryLlmClient(propertiesFor(baseUrl(server), ""));

        client.call("hello");

        assertThat(capturedAuthHeader.toString()).isEmpty();
    }

    @Test
    void sendsModelFromConfigurationNotFromCaller() throws Exception {
        StringBuilder capturedRequestBody = new StringBuilder();
        server = startServer(200, "{}", null, capturedRequestBody);
        PrimaryLlmClient client = new PrimaryLlmClient(propertiesFor(baseUrl(server), "token"));

        client.call("hello");

        assertThat(capturedRequestBody.toString()).contains("\"model\":\"llama3.3-70b-instruct\"");
        assertThat(capturedRequestBody.toString()).contains("\"content\":\"hello\"");
    }

    @Test
    void returnsFailureResultForHttpErrorStatus() throws Exception {
        server = startServer(401, "{\"error\":\"unauthorized\"}", null, null);
        PrimaryLlmClient client = new PrimaryLlmClient(propertiesFor(baseUrl(server), "bad-token"));

        PrimaryLlmResult result = client.call("hello");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatusCode()).isEqualTo(401);
        assertThat(result.getErrorMessage()).isNotNull();
    }

    @Test
    void returnsFailureResultWhenConnectionRefused() {
        ShadowProxyProperties properties = propertiesFor("http://localhost:1/chat/completions", "token");
        PrimaryLlmClient client = new PrimaryLlmClient(properties);

        PrimaryLlmResult result = client.call("hello");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatusCode()).isEqualTo(0);
        assertThat(result.getErrorMessage()).isNotNull();
    }
}
