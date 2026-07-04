package com.example.demo.common.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShadowProxyPropertiesTest {

    @Test
    void defaultsAreSensible() {
        ShadowProxyProperties properties = new ShadowProxyProperties();

        assertThat(properties.getServiceName()).isEqualTo("llm-shadow-proxy");
        assertThat(properties.getMachineIdEnvVar()).isEqualTo("MACHINE_ID");
        assertThat(properties.getShadow().isEnabled()).isTrue();
        assertThat(properties.getShadow().getComparisonMode()).isEqualTo("NORMALIZED_JSON");
        assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(5);
        assertThat(properties.getRetry().getInitialBackoffMs()).isEqualTo(500);
        assertThat(properties.getRetry().getBackoffMultiplier()).isEqualTo(2.0);
        assertThat(properties.getRetry().getMaxBackoffMs()).isEqualTo(30000);
        assertThat(properties.getRetry().isJitterEnabled()).isTrue();
        assertThat(properties.getRedaction().getMaxPersistedPayloadSize()).isEqualTo(10000);
        assertThat(properties.getRedaction().getFields()).isEmpty();
        assertThat(properties.getPrimaryLlm().getConnectTimeoutMs()).isEqualTo(2000);
        assertThat(properties.getPrimaryLlm().getReadTimeoutMs()).isEqualTo(5000);
    }

    @Test
    void settersOverrideDefaultsForTopLevelFields() {
        ShadowProxyProperties properties = new ShadowProxyProperties();

        properties.setServiceName("custom-service");
        properties.setMachineIdEnvVar("CUSTOM_MACHINE_ID");

        assertThat(properties.getServiceName()).isEqualTo("custom-service");
        assertThat(properties.getMachineIdEnvVar()).isEqualTo("CUSTOM_MACHINE_ID");
    }

    @Test
    void llmEndpointSettersRoundTrip() {
        ShadowProxyProperties.LlmEndpoint endpoint = new ShadowProxyProperties.LlmEndpoint();

        endpoint.setBaseUrl("https://inference.example.com/v1/chat/completions");
        endpoint.setAuthToken("secret-token");
        endpoint.setDefaultModel("llama3.3-70b-instruct");
        endpoint.setConnectTimeoutMs(1234);
        endpoint.setReadTimeoutMs(5678);

        assertThat(endpoint.getBaseUrl()).isEqualTo("https://inference.example.com/v1/chat/completions");
        assertThat(endpoint.getAuthToken()).isEqualTo("secret-token");
        assertThat(endpoint.getDefaultModel()).isEqualTo("llama3.3-70b-instruct");
        assertThat(endpoint.getConnectTimeoutMs()).isEqualTo(1234);
        assertThat(endpoint.getReadTimeoutMs()).isEqualTo(5678);
    }

    @Test
    void kafkaTopicsSettersRoundTrip() {
        ShadowProxyProperties.Kafka.Topics topics = new ShadowProxyProperties.Kafka.Topics();

        topics.setShadowRequests("shadow-requests");
        topics.setShadowRetry("shadow-retry");
        topics.setShadowDlq("shadow-dlq");

        assertThat(topics.getShadowRequests()).isEqualTo("shadow-requests");
        assertThat(topics.getShadowRetry()).isEqualTo("shadow-retry");
        assertThat(topics.getShadowDlq()).isEqualTo("shadow-dlq");

        ShadowProxyProperties.Kafka kafka = new ShadowProxyProperties.Kafka();
        kafka.setTopics(topics);
        assertThat(kafka.getTopics()).isSameAs(topics);
    }

    @Test
    void redactionSettersRoundTrip() {
        ShadowProxyProperties.Redaction redaction = new ShadowProxyProperties.Redaction();

        redaction.setMaxPersistedPayloadSize(500);
        redaction.setFields(List.of("ssn", "creditCard"));

        assertThat(redaction.getMaxPersistedPayloadSize()).isEqualTo(500);
        assertThat(redaction.getFields()).containsExactly("ssn", "creditCard");
    }
}
