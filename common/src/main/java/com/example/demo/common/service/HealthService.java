package com.example.demo.common.service;

import com.example.demo.common.dto.HealthOutputDto;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Shared health check used by every deployable module (proxy-api, shadow-worker, ...).
 * Reports connectivity to the infrastructure every module depends on: MySQL and Kafka.
 *
 * Module-specific checks (e.g. primary/candidate LLM connectivity) should be added
 * alongside this in each module rather than here.
 */
@Service
public class HealthService {

    private static final int KAFKA_CHECK_TIMEOUT_SECONDS = 5;

    private final JdbcTemplate jdbcTemplate;
    private final KafkaAdmin kafkaAdmin;

    public HealthService(JdbcTemplate jdbcTemplate, KafkaAdmin kafkaAdmin) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaAdmin = kafkaAdmin;
    }

    public HealthOutputDto checkHealth() {
        Instant mysqlTimestamp = fetchMysqlTimestamp();
        Instant kafkaTimestamp = fetchKafkaTimestamp();

        String status = mysqlTimestamp != null && kafkaTimestamp != null ? "OK" : "DEGRADED";

        return new HealthOutputDto(status, Instant.now(), mysqlTimestamp, kafkaTimestamp);
    }

    private Instant fetchMysqlTimestamp() {
        try {
            Timestamp timestamp = jdbcTemplate.queryForObject("SELECT UTC_TIMESTAMP(6)", Timestamp.class);
            return timestamp != null ? timestamp.toInstant() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Instant fetchKafkaTimestamp() {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            adminClient.describeCluster().clusterId().get(KAFKA_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return Instant.now();
        } catch (Exception ignored) {
            return null;
        }
    }
}
