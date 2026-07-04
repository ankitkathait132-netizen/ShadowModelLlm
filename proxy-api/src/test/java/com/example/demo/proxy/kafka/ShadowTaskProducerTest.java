package com.example.demo.proxy.kafka;

import com.example.demo.common.config.ShadowProxyProperties;
import com.example.demo.common.dto.ShadowTaskDto;
import com.example.demo.common.logging.LogEvent;
import com.example.demo.common.logging.StructuredLogger;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class ShadowTaskProducerTest {

    private KafkaTemplate<String, ShadowTaskDto> kafkaTemplate;
    private StructuredLogger structuredLogger;
    private ShadowProxyProperties properties;
    private ShadowTaskProducer producer;

    @BeforeEach
    void setUp() {
        kafkaTemplate = Mockito.mock(KafkaTemplate.class);
        structuredLogger = Mockito.mock(StructuredLogger.class);
        properties = new ShadowProxyProperties();
        properties.getKafka().getTopics().setShadowRequests("llm-shadow-requests");

        producer = new ShadowTaskProducer(kafkaTemplate, properties, structuredLogger);
    }

    private ShadowTaskDto sampleTask() {
        return ShadowTaskDto.builder()
                .shadowTaskId("task-1")
                .correlationId("corr-1")
                .primaryModel("primary-model")
                .candidateModel("candidate-model")
                .build();
    }

    @Test
    void publishesToConfiguredTopicWithShadowTaskIdAsKey() {
        ShadowTaskDto task = sampleTask();
        CompletableFuture<SendResult<String, ShadowTaskDto>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(eq("llm-shadow-requests"), eq("task-1"), eq(task))).thenReturn(future);

        producer.publish(task);

        verify(kafkaTemplate).send("llm-shadow-requests", "task-1", task);
    }

    @Test
    void logsSuccessEventWhenKafkaSendCompletesSuccessfully() {
        ShadowTaskDto task = sampleTask();
        ProducerRecord<String, ShadowTaskDto> record = new ProducerRecord<>("llm-shadow-requests", task);
        RecordMetadata metadata = new RecordMetadata(new TopicPartition("llm-shadow-requests", 0), 0, 0, 0, 0, 0);
        SendResult<String, ShadowTaskDto> sendResult = new SendResult<>(record, metadata);

        CompletableFuture<SendResult<String, ShadowTaskDto>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        producer.publish(task);

        ArgumentCaptor<LogEvent> captor = ArgumentCaptor.forClass(LogEvent.class);
        verify(structuredLogger).log(captor.capture());

        LogEvent logged = captor.getValue();
        assertThat(logged.getEvent()).isEqualTo("shadow.enqueued");
        assertThat(logged.getStatus()).isEqualTo("success");
        assertThat(logged.getCorrelationId()).isEqualTo("corr-1");
        assertThat(logged.getShadowTaskId()).isEqualTo("task-1");
    }

    @Test
    void logsFailureEventWhenKafkaSendFails() {
        ShadowTaskDto task = sampleTask();
        CompletableFuture<SendResult<String, ShadowTaskDto>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("broker unavailable"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        producer.publish(task);

        ArgumentCaptor<LogEvent> captor = ArgumentCaptor.forClass(LogEvent.class);
        verify(structuredLogger).log(captor.capture());

        LogEvent logged = captor.getValue();
        assertThat(logged.getEvent()).isEqualTo("shadow.enqueue.failed");
        assertThat(logged.getStatus()).isEqualTo("failure");
        assertThat(logged.getErrorType()).isEqualTo("kafka_publish_error");
        assertThat(logged.getMessage()).contains("broker unavailable");
    }
}
