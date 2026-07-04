package com.example.demo.common.service;

import com.example.demo.common.dto.HealthOutputDto;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.common.KafkaFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaAdmin;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class HealthServiceTest {

    private JdbcTemplate jdbcTemplate;
    private KafkaAdmin kafkaAdmin;
    private AdminClient adminClient;
    private MockedStatic<AdminClient> adminClientStatic;

    @BeforeEach
    void setUp() {
        jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        kafkaAdmin = Mockito.mock(KafkaAdmin.class);
        adminClient = Mockito.mock(AdminClient.class);

        Map<String, Object> kafkaConfig = Collections.singletonMap("bootstrap.servers", "localhost:9092");
        when(kafkaAdmin.getConfigurationProperties()).thenReturn(kafkaConfig);

        adminClientStatic = mockStatic(AdminClient.class);
        adminClientStatic.when(() -> AdminClient.create(kafkaConfig)).thenReturn(adminClient);
    }

    @AfterEach
    void tearDown() {
        adminClientStatic.close();
    }

    @SuppressWarnings("unchecked")
    private void stubDescribeClusterSuccess() throws Exception {
        DescribeClusterResult describeClusterResult = Mockito.mock(DescribeClusterResult.class);
        KafkaFuture<String> future = Mockito.mock(KafkaFuture.class);
        when(future.get(anyLong(), any())).thenReturn("test-cluster-id");
        when(describeClusterResult.clusterId()).thenReturn(future);
        when(adminClient.describeCluster()).thenReturn(describeClusterResult);
    }

    @SuppressWarnings("unchecked")
    private void stubDescribeClusterFailure() throws Exception {
        DescribeClusterResult describeClusterResult = Mockito.mock(DescribeClusterResult.class);
        KafkaFuture<String> future = Mockito.mock(KafkaFuture.class);
        when(future.get(anyLong(), any())).thenThrow(new ExecutionException("boom", new RuntimeException("boom")));
        when(describeClusterResult.clusterId()).thenReturn(future);
        when(adminClient.describeCluster()).thenReturn(describeClusterResult);
    }

    @Test
    void checkHealthReturnsOkWhenMysqlAndKafkaAreReachable() throws Exception {
        Timestamp now = Timestamp.from(Instant.now());
        when(jdbcTemplate.queryForObject("SELECT UTC_TIMESTAMP(6)", Timestamp.class)).thenReturn(now);
        stubDescribeClusterSuccess();

        HealthService healthService = new HealthService(jdbcTemplate, kafkaAdmin);
        HealthOutputDto result = healthService.checkHealth();

        assertThat(result.getStatus()).isEqualTo("OK");
        assertThat(result.getMysqlTimestamp()).isEqualTo(now.toInstant());
        assertThat(result.getKafkaTimestamp()).isNotNull();
        assertThat(result.getTimestamp()).isNotNull();
    }

    @Test
    void checkHealthReturnsDegradedWhenMysqlQueryThrows() throws Exception {
        when(jdbcTemplate.queryForObject("SELECT UTC_TIMESTAMP(6)", Timestamp.class))
                .thenThrow(new RuntimeException("connection refused"));
        stubDescribeClusterSuccess();

        HealthService healthService = new HealthService(jdbcTemplate, kafkaAdmin);
        HealthOutputDto result = healthService.checkHealth();

        assertThat(result.getStatus()).isEqualTo("DEGRADED");
        assertThat(result.getMysqlTimestamp()).isNull();
        assertThat(result.getKafkaTimestamp()).isNotNull();
    }

    @Test
    void checkHealthReturnsDegradedWhenKafkaDescribeClusterFails() throws Exception {
        Timestamp now = Timestamp.from(Instant.now());
        when(jdbcTemplate.queryForObject("SELECT UTC_TIMESTAMP(6)", Timestamp.class)).thenReturn(now);
        stubDescribeClusterFailure();

        HealthService healthService = new HealthService(jdbcTemplate, kafkaAdmin);
        HealthOutputDto result = healthService.checkHealth();

        assertThat(result.getStatus()).isEqualTo("DEGRADED");
        assertThat(result.getMysqlTimestamp()).isNotNull();
        assertThat(result.getKafkaTimestamp()).isNull();
    }

    @Test
    void checkHealthReturnsDegradedWhenMysqlReturnsNullTimestamp() throws Exception {
        when(jdbcTemplate.queryForObject("SELECT UTC_TIMESTAMP(6)", Timestamp.class)).thenReturn(null);
        stubDescribeClusterFailure();

        HealthService healthService = new HealthService(jdbcTemplate, kafkaAdmin);
        HealthOutputDto result = healthService.checkHealth();

        assertThat(result.getStatus()).isEqualTo("DEGRADED");
        assertThat(result.getMysqlTimestamp()).isNull();
    }
}
