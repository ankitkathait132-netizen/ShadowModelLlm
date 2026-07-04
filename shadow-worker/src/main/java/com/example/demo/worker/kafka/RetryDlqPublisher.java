package com.example.demo.worker.kafka;

import com.example.demo.common.config.ShadowProxyProperties;
import com.example.demo.common.dto.ShadowTaskDto;
import com.example.demo.common.logging.LogEvent;
import com.example.demo.common.logging.StructuredLogger;
import com.example.demo.common.util.BackoffCalculator;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Routes a failed shadow task to the retry topic (with incremented attempt metadata
 * and a bounded exponential backoff + jitter delay) or, once attempts are exhausted,
 * to the DLQ topic.
 *
 * Note: the backoff delay is enforced with a blocking sleep on the consumer thread.
 * This is a simplification for the MVP; a production setup would use a non-blocking
 * delayed-retry mechanism instead.
 */
@Component
public class RetryDlqPublisher {

    private static final String COMPONENT = "shadow-worker";

    private final KafkaTemplate<String, ShadowTaskDto> shadowTaskKafkaTemplate;
    private final ShadowProxyProperties properties;
    private final StructuredLogger structuredLogger;

    public RetryDlqPublisher(KafkaTemplate<String, ShadowTaskDto> shadowTaskKafkaTemplate,
                              ShadowProxyProperties properties, StructuredLogger structuredLogger) {
        this.shadowTaskKafkaTemplate = shadowTaskKafkaTemplate;
        this.properties = properties;
        this.structuredLogger = structuredLogger;
    }

    public void handleFailure(ShadowTaskDto task, String errorType, String errorMessage) {
        task.setLastErrorType(errorType);
        task.setLastErrorMessage(errorMessage);

        if (task.getAttempt() < task.getMaxAttempts()) {
            scheduleRetry(task, errorType, errorMessage);
        } else {
            publishToDlq(task, errorType, errorMessage);
        }
    }

    private void scheduleRetry(ShadowTaskDto task, String errorType, String errorMessage) {
        long delayMs = BackoffCalculator.computeDelayMs(properties.getRetry(), task.getAttempt());
        task.setAttempt(task.getAttempt() + 1);

        structuredLogger.log(LogEvent.builder("shadow.retry.scheduled")
                .component(COMPONENT)
                .operation("candidate_call")
                .correlationId(task.getCorrelationId())
                .shadowTaskId(task.getShadowTaskId())
                .attempt(task.getAttempt())
                .status("retry")
                .durationMs(delayMs)
                .errorType(errorType)
                .message(errorMessage)
                .build());

        sleepQuietly(delayMs);

        String topic = properties.getKafka().getTopics().getShadowRetry();
        shadowTaskKafkaTemplate.send(topic, task.getShadowTaskId(), task);
    }

    private void publishToDlq(ShadowTaskDto task, String errorType, String errorMessage) {
        structuredLogger.log(LogEvent.builder("shadow.dlq.published")
                .component(COMPONENT)
                .operation("candidate_call")
                .correlationId(task.getCorrelationId())
                .shadowTaskId(task.getShadowTaskId())
                .attempt(task.getAttempt())
                .status("failure")
                .errorType(errorType)
                .message(errorMessage)
                .build());

        String topic = properties.getKafka().getTopics().getShadowDlq();
        shadowTaskKafkaTemplate.send(topic, task.getShadowTaskId(), task);
    }

    private void sleepQuietly(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
