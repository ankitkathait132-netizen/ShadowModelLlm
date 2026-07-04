package com.example.demo.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.demo.common.config.ShadowProxyProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class StructuredLoggerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MachineIdResolver machineIdResolver;
    private ShadowProxyProperties properties;
    private StructuredLogger structuredLogger;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger logbackLogger;

    @BeforeEach
    void setUp() {
        machineIdResolver = Mockito.mock(MachineIdResolver.class);
        when(machineIdResolver.resolve()).thenReturn("machine-123");

        properties = new ShadowProxyProperties();
        properties.setServiceName("llm-shadow-proxy");

        structuredLogger = new StructuredLogger(objectMapper, machineIdResolver, properties);

        logbackLogger = (Logger) LoggerFactory.getLogger(StructuredLogger.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logbackLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        logbackLogger.detachAppender(listAppender);
    }

    @Test
    void logsAtInfoLevelWithExpectedFieldsWhenNoErrorType() throws Exception {
        LogEvent event = LogEvent.builder("shadow.match")
                .component("shadow-worker")
                .operation("compare")
                .correlationId("corr-1")
                .shadowTaskId("task-1")
                .status("matched")
                .build();

        structuredLogger.log(event);

        assertThat(listAppender.list).hasSize(1);
        ILoggingEvent logged = listAppender.list.get(0);
        assertThat(logged.getLevel()).isEqualTo(Level.INFO);

        JsonNode json = objectMapper.readTree(logged.getFormattedMessage());
        assertThat(json.get("event").asText()).isEqualTo("shadow.match");
        assertThat(json.get("serviceName").asText()).isEqualTo("llm-shadow-proxy");
        assertThat(json.get("component").asText()).isEqualTo("shadow-worker");
        assertThat(json.get("machineId").asText()).isEqualTo("machine-123");
        assertThat(json.get("correlationId").asText()).isEqualTo("corr-1");
        assertThat(json.has("errorType")).isFalse();
    }

    @Test
    void logsAtWarnLevelWhenErrorTypePresent() throws Exception {
        LogEvent event = LogEvent.builder("shadow.candidate.failed")
                .component("shadow-worker")
                .operation("candidate_call")
                .errorType("candidate_call_error")
                .message("timed out")
                .build();

        structuredLogger.log(event);

        assertThat(listAppender.list).hasSize(1);
        ILoggingEvent logged = listAppender.list.get(0);
        assertThat(logged.getLevel()).isEqualTo(Level.WARN);

        JsonNode json = objectMapper.readTree(logged.getFormattedMessage());
        assertThat(json.get("errorType").asText()).isEqualTo("candidate_call_error");
        assertThat(json.get("message").asText()).isEqualTo("timed out");
    }

    @Test
    void omitsNullOptionalFieldsFromOutput() throws Exception {
        LogEvent event = LogEvent.builder("proxy.request.received").build();

        structuredLogger.log(event);

        JsonNode json = objectMapper.readTree(listAppender.list.get(0).getFormattedMessage());
        assertThat(json.has("correlationId")).isFalse();
        assertThat(json.has("shadowTaskId")).isFalse();
        assertThat(json.has("attempt")).isFalse();
        assertThat(json.has("durationMs")).isFalse();
        assertThat(json.has("primaryModel")).isFalse();
        assertThat(json.has("candidateModel")).isFalse();
    }
}
