package com.example.demo.worker.kafka;

import com.example.demo.common.config.ShadowProxyProperties;
import com.example.demo.common.dto.ShadowTaskDto;
import com.example.demo.common.logging.LogEvent;
import com.example.demo.common.logging.StructuredLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SuppressWarnings("unchecked")
class RetryDlqPublisherTest {

    private KafkaTemplate<String, ShadowTaskDto> kafkaTemplate;
    private StructuredLogger structuredLogger;
    private ShadowProxyProperties properties;
    private RetryDlqPublisher publisher;

    @BeforeEach
    void setUp() {
        kafkaTemplate = Mockito.mock(KafkaTemplate.class);
        structuredLogger = Mockito.mock(StructuredLogger.class);

        properties = new ShadowProxyProperties();
        properties.getKafka().getTopics().setShadowRetry("llm-shadow-requests-retry");
        properties.getKafka().getTopics().setShadowDlq("llm-shadow-requests-dlq");
        properties.getRetry().setInitialBackoffMs(1);
        properties.getRetry().setMaxBackoffMs(1);
        properties.getRetry().setBackoffMultiplier(1.0);
        properties.getRetry().setJitterEnabled(false);

        publisher = new RetryDlqPublisher(kafkaTemplate, properties, structuredLogger);
    }

    private ShadowTaskDto taskWithAttempt(int attempt, int maxAttempts) {
        return ShadowTaskDto.builder()
                .shadowTaskId("task-1")
                .correlationId("corr-1")
                .attempt(attempt)
                .maxAttempts(maxAttempts)
                .build();
    }

    @Test
    void schedulesRetryWhenAttemptsRemain() {
        ShadowTaskDto task = taskWithAttempt(1, 5);

        publisher.handleFailure(task, "candidate_call_error", "timed out");

        assertThat(task.getAttempt()).isEqualTo(2);
        assertThat(task.getLastErrorType()).isEqualTo("candidate_call_error");
        assertThat(task.getLastErrorMessage()).isEqualTo("timed out");
        verify(kafkaTemplate).send("llm-shadow-requests-retry", "task-1", task);

        ArgumentCaptor<LogEvent> captor = ArgumentCaptor.forClass(LogEvent.class);
        verify(structuredLogger).log(captor.capture());
        assertThat(captor.getValue().getEvent()).isEqualTo("shadow.retry.scheduled");
        assertThat(captor.getValue().getStatus()).isEqualTo("retry");
    }

    @Test
    void publishesToDlqWhenAttemptsExhausted() {
        ShadowTaskDto task = taskWithAttempt(5, 5);

        publisher.handleFailure(task, "candidate_call_error", "still failing");

        assertThat(task.getAttempt()).isEqualTo(5);
        verify(kafkaTemplate).send("llm-shadow-requests-dlq", "task-1", task);

        ArgumentCaptor<LogEvent> captor = ArgumentCaptor.forClass(LogEvent.class);
        verify(structuredLogger).log(captor.capture());
        assertThat(captor.getValue().getEvent()).isEqualTo("shadow.dlq.published");
        assertThat(captor.getValue().getStatus()).isEqualTo("failure");
    }

    @Test
    void doesNotPublishToRetryTopicWhenAttemptsExhausted() {
        ShadowTaskDto task = taskWithAttempt(5, 5);

        publisher.handleFailure(task, "db_write_error", "duplicate key");

        verify(kafkaTemplate, never()).send(eq("llm-shadow-requests-retry"), anyString(), any());
    }
}
