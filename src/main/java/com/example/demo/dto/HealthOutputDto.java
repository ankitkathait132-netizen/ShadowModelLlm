package com.example.demo.dto;

import java.time.Instant;

public class HealthOutputDto {

    private String status;
    private Instant timestamp;
    private Instant mysqlTimestamp;
    private Instant kafkaTimestamp;

    public HealthOutputDto() {
    }

    public HealthOutputDto(String status, Instant timestamp, Instant mysqlTimestamp, Instant kafkaTimestamp) {
        this.status = status;
        this.timestamp = timestamp;
        this.mysqlTimestamp = mysqlTimestamp;
        this.kafkaTimestamp = kafkaTimestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Instant getMysqlTimestamp() {
        return mysqlTimestamp;
    }

    public void setMysqlTimestamp(Instant mysqlTimestamp) {
        this.mysqlTimestamp = mysqlTimestamp;
    }

    public Instant getKafkaTimestamp() {
        return kafkaTimestamp;
    }

    public void setKafkaTimestamp(Instant kafkaTimestamp) {
        this.kafkaTimestamp = kafkaTimestamp;
    }
}
