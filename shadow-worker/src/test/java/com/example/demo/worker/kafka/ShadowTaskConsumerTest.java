package com.example.demo.worker.kafka;

import com.example.demo.common.dto.ShadowTaskDto;
import com.example.demo.worker.service.ShadowWorkerService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.verify;

class ShadowTaskConsumerTest {

    @Test
    void delegatesReceivedMessageToShadowWorkerService() {
        ShadowWorkerService shadowWorkerService = Mockito.mock(ShadowWorkerService.class);
        ShadowTaskConsumer consumer = new ShadowTaskConsumer(shadowWorkerService);

        ShadowTaskDto task = ShadowTaskDto.builder().shadowTaskId("task-1").build();
        consumer.onMessage(task);

        verify(shadowWorkerService).process(task);
    }
}
