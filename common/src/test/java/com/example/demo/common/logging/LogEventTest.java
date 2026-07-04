package com.example.demo.common.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogEventTest {

    @Test
    void builderPopulatesAllFields() {
        LogEvent event = LogEvent.builder("shadow.mismatch")
                .component("shadow-worker")
                .operation("compare")
                .correlationId("corr-1")
                .shadowTaskId("task-1")
                .attempt(2)
                .status("mismatched")
                .durationMs(150L)
                .primaryModel("primary-model")
                .candidateModel("candidate-model")
                .errorType("json_extraction_degraded")
                .message("payloads differ")
                .build();

        assertThat(event.getEvent()).isEqualTo("shadow.mismatch");
        assertThat(event.getComponent()).isEqualTo("shadow-worker");
        assertThat(event.getOperation()).isEqualTo("compare");
        assertThat(event.getCorrelationId()).isEqualTo("corr-1");
        assertThat(event.getShadowTaskId()).isEqualTo("task-1");
        assertThat(event.getAttempt()).isEqualTo(2);
        assertThat(event.getStatus()).isEqualTo("mismatched");
        assertThat(event.getDurationMs()).isEqualTo(150L);
        assertThat(event.getPrimaryModel()).isEqualTo("primary-model");
        assertThat(event.getCandidateModel()).isEqualTo("candidate-model");
        assertThat(event.getErrorType()).isEqualTo("json_extraction_degraded");
        assertThat(event.getMessage()).isEqualTo("payloads differ");
    }

    @Test
    void onlyEventIsRequiredAndOptionalFieldsDefaultToNull() {
        LogEvent event = LogEvent.builder("shadow.task.received").build();

        assertThat(event.getEvent()).isEqualTo("shadow.task.received");
        assertThat(event.getComponent()).isNull();
        assertThat(event.getOperation()).isNull();
        assertThat(event.getCorrelationId()).isNull();
        assertThat(event.getShadowTaskId()).isNull();
        assertThat(event.getAttempt()).isNull();
        assertThat(event.getStatus()).isNull();
        assertThat(event.getDurationMs()).isNull();
        assertThat(event.getErrorType()).isNull();
        assertThat(event.getMessage()).isNull();
    }
}
