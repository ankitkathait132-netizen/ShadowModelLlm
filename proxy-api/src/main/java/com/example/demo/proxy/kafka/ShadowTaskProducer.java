package com.example.demo.proxy.kafka;

import com.example.demo.common.config.ShadowProxyProperties;
import com.example.demo.common.dto.ShadowTaskDto;
import com.example.demo.common.logging.LogEvent;
import com.example.demo.common.logging.StructuredLogger;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class ShadowTaskProducer {

    private static final String COMPONENT = "proxy-api";

    private final KafkaTemplate<String, ShadowTaskDto> shadowTaskKafkaTemplate;
    private final ShadowProxyProperties properties;
    private final StructuredLogger structuredLogger;

    public ShadowTaskProducer(KafkaTemplate<String, ShadowTaskDto> shadowTaskKafkaTemplate,
                               ShadowProxyProperties properties,
                               StructuredLogger structuredLogger) {
        this.shadowTaskKafkaTemplate = shadowTaskKafkaTemplate;
        this.properties = properties;
        this.structuredLogger = structuredLogger;
    }

    public void publish(ShadowTaskDto task) {
        String topic = properties.getKafka().getTopics().getShadowRequests();
        long start = System.currentTimeMillis();

        shadowTaskKafkaTemplate.send(topic, task.getShadowTaskId(), task).whenComplete((result, ex) -> {
            long durationMs = System.currentTimeMillis() - start;
            if (ex == null) {
                structuredLogger.log(LogEvent.builder("shadow.enqueued")
                        .component(COMPONENT)
                        .operation("kafka_publish")
                        .correlationId(task.getCorrelationId())
                        .shadowTaskId(task.getShadowTaskId())
                        .status("success")
                        .durationMs(durationMs)
                        .primaryModel(task.getPrimaryModel())
                        .candidateModel(task.getCandidateModel())
                        .build());
            } else {
                structuredLogger.log(LogEvent.builder("shadow.enqueue.failed")
                        .component(COMPONENT)
                        .operation("kafka_publish")
                        .correlationId(task.getCorrelationId())
                        .shadowTaskId(task.getShadowTaskId())
                        .status("failure")
                        .durationMs(durationMs)
                        .errorType("kafka_publish_error")
                        .message(ex.getMessage())
                        .build());
            }
        });
    }
}
