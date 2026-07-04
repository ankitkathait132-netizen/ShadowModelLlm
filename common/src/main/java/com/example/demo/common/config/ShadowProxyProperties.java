package com.example.demo.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public class ShadowProxyProperties {

    private String serviceName = "llm-shadow-proxy";
    private String machineIdEnvVar = "MACHINE_ID";
    private Shadow shadow = new Shadow();
    private LlmEndpoint primaryLlm = new LlmEndpoint();
    private LlmEndpoint candidateLlm = new LlmEndpoint();
    private Kafka kafka = new Kafka();
    private Retry retry = new Retry();
    private Redaction redaction = new Redaction();

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getMachineIdEnvVar() {
        return machineIdEnvVar;
    }

    public void setMachineIdEnvVar(String machineIdEnvVar) {
        this.machineIdEnvVar = machineIdEnvVar;
    }

    public Shadow getShadow() {
        return shadow;
    }

    public void setShadow(Shadow shadow) {
        this.shadow = shadow;
    }

    public LlmEndpoint getPrimaryLlm() {
        return primaryLlm;
    }

    public void setPrimaryLlm(LlmEndpoint primaryLlm) {
        this.primaryLlm = primaryLlm;
    }

    public LlmEndpoint getCandidateLlm() {
        return candidateLlm;
    }

    public void setCandidateLlm(LlmEndpoint candidateLlm) {
        this.candidateLlm = candidateLlm;
    }

    public Kafka getKafka() {
        return kafka;
    }

    public void setKafka(Kafka kafka) {
        this.kafka = kafka;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry;
    }

    public Redaction getRedaction() {
        return redaction;
    }

    public void setRedaction(Redaction redaction) {
        this.redaction = redaction;
    }

    public static class Shadow {
        private boolean enabled = true;
        private String comparisonMode = "NORMALIZED_JSON";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getComparisonMode() {
            return comparisonMode;
        }

        public void setComparisonMode(String comparisonMode) {
            this.comparisonMode = comparisonMode;
        }
    }

    public static class LlmEndpoint {
        private String baseUrl;
        private String authToken;
        private String defaultModel;
        private int connectTimeoutMs = 2000;
        private int readTimeoutMs = 5000;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getAuthToken() {
            return authToken;
        }

        public void setAuthToken(String authToken) {
            this.authToken = authToken;
        }

        public String getDefaultModel() {
            return defaultModel;
        }

        public void setDefaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }
    }

    public static class Kafka {
        private Topics topics = new Topics();

        public Topics getTopics() {
            return topics;
        }

        public void setTopics(Topics topics) {
            this.topics = topics;
        }

        public static class Topics {
            private String shadowRequests;
            private String shadowRetry;
            private String shadowDlq;

            public String getShadowRequests() {
                return shadowRequests;
            }

            public void setShadowRequests(String shadowRequests) {
                this.shadowRequests = shadowRequests;
            }

            public String getShadowRetry() {
                return shadowRetry;
            }

            public void setShadowRetry(String shadowRetry) {
                this.shadowRetry = shadowRetry;
            }

            public String getShadowDlq() {
                return shadowDlq;
            }

            public void setShadowDlq(String shadowDlq) {
                this.shadowDlq = shadowDlq;
            }
        }
    }

    public static class Retry {
        private int maxAttempts = 5;
        private long initialBackoffMs = 500;
        private double backoffMultiplier = 2.0;
        private long maxBackoffMs = 30000;
        private boolean jitterEnabled = true;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getInitialBackoffMs() {
            return initialBackoffMs;
        }

        public void setInitialBackoffMs(long initialBackoffMs) {
            this.initialBackoffMs = initialBackoffMs;
        }

        public double getBackoffMultiplier() {
            return backoffMultiplier;
        }

        public void setBackoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
        }

        public long getMaxBackoffMs() {
            return maxBackoffMs;
        }

        public void setMaxBackoffMs(long maxBackoffMs) {
            this.maxBackoffMs = maxBackoffMs;
        }

        public boolean isJitterEnabled() {
            return jitterEnabled;
        }

        public void setJitterEnabled(boolean jitterEnabled) {
            this.jitterEnabled = jitterEnabled;
        }
    }

    public static class Redaction {
        private int maxPersistedPayloadSize = 10000;
        private List<String> fields = new ArrayList<>();

        public int getMaxPersistedPayloadSize() {
            return maxPersistedPayloadSize;
        }

        public void setMaxPersistedPayloadSize(int maxPersistedPayloadSize) {
            this.maxPersistedPayloadSize = maxPersistedPayloadSize;
        }

        public List<String> getFields() {
            return fields;
        }

        public void setFields(List<String> fields) {
            this.fields = fields;
        }
    }
}
