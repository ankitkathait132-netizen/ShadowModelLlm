package com.example.demo.common.logging;

import com.example.demo.common.config.ShadowProxyProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared structured JSON logger used by every module (proxy-api, shadow-worker, ...),
 * so log lines are consistent across a multi-host deployment.
 */
@Component
public class StructuredLogger {

    private static final Logger log = LoggerFactory.getLogger(StructuredLogger.class);

    private final ObjectMapper objectMapper;
    private final MachineIdResolver machineIdResolver;
    private final ShadowProxyProperties properties;

    public StructuredLogger(ObjectMapper objectMapper, MachineIdResolver machineIdResolver,
                             ShadowProxyProperties properties) {
        this.objectMapper = objectMapper;
        this.machineIdResolver = machineIdResolver;
        this.properties = properties;
    }

    public void log(LogEvent logEvent) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("event", logEvent.getEvent());
        fields.put("serviceName", properties.getServiceName());
        fields.put("component", logEvent.getComponent());
        fields.put("operation", logEvent.getOperation());
        fields.put("machineId", machineIdResolver.resolve());
        putIfPresent(fields, "correlationId", logEvent.getCorrelationId());
        putIfPresent(fields, "shadowTaskId", logEvent.getShadowTaskId());
        putIfPresent(fields, "attempt", logEvent.getAttempt());
        putIfPresent(fields, "status", logEvent.getStatus());
        putIfPresent(fields, "durationMs", logEvent.getDurationMs());
        putIfPresent(fields, "primaryModel", logEvent.getPrimaryModel());
        putIfPresent(fields, "candidateModel", logEvent.getCandidateModel());
        putIfPresent(fields, "errorType", logEvent.getErrorType());
        putIfPresent(fields, "message", logEvent.getMessage());

        String json = writeAsJson(fields);
        if (logEvent.getErrorType() != null) {
            log.warn(json);
        } else {
            log.info(json);
        }
    }

    private void putIfPresent(Map<String, Object> fields, String key, Object value) {
        if (value != null) {
            fields.put(key, value);
        }
    }

    private String writeAsJson(Map<String, Object> fields) {
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            return fields.toString();
        }
    }
}
