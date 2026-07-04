package com.example.demo.common.config;

import com.example.demo.common.dto.ShadowTaskDto;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared typed KafkaTemplate for ShadowTaskDto, used by proxy-api (publishes to
 * shadow-requests) and shadow-worker (publishes to shadow-retry / shadow-dlq),
 * rather than relying on Boot's default untyped KafkaTemplate&lt;Object, Object&gt; bean.
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, ShadowTaskDto> shadowTaskProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, ShadowTaskDto> shadowTaskKafkaTemplate(
            ProducerFactory<String, ShadowTaskDto> shadowTaskProducerFactory) {
        return new KafkaTemplate<>(shadowTaskProducerFactory);
    }
}
