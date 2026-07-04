package com.example.demo.worker.kafka;

import com.example.demo.common.dto.ShadowTaskDto;
import com.example.demo.worker.service.ShadowWorkerService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ShadowTaskConsumer {

    private final ShadowWorkerService shadowWorkerService;

    public ShadowTaskConsumer(ShadowWorkerService shadowWorkerService) {
        this.shadowWorkerService = shadowWorkerService;
    }

    @KafkaListener(
            topics = {"${app.kafka.topics.shadow-requests}", "${app.kafka.topics.shadow-retry}"},
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onMessage(ShadowTaskDto task) {
        shadowWorkerService.process(task);
    }
}
