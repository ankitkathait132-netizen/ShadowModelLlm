package com.example.demo.worker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "llm_shadow_mismatch", uniqueConstraints = @UniqueConstraint(columnNames = "shadow_task_id"))
public class LlmShadowMismatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shadow_task_id", nullable = false, unique = true)
    private String shadowTaskId;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "request_hash")
    private String requestHash;

    @Lob
    @Column(name = "request_payload_redacted")
    private String requestPayloadRedacted;

    @Column(name = "primary_model")
    private String primaryModel;

    @Column(name = "candidate_model")
    private String candidateModel;

    @Column(name = "primary_status")
    private Integer primaryStatus;

    @Column(name = "candidate_status")
    private Integer candidateStatus;

    @Column(name = "primary_latency_ms")
    private Long primaryLatencyMs;

    @Column(name = "candidate_latency_ms")
    private Long candidateLatencyMs;

    @Lob
    @Column(name = "primary_raw_output")
    private String primaryRawOutput;

    @Lob
    @Column(name = "candidate_raw_output")
    private String candidateRawOutput;

    @Lob
    @Column(name = "primary_json_output")
    private String primaryJsonOutput;

    @Lob
    @Column(name = "candidate_json_output")
    private String candidateJsonOutput;

    @Column(name = "diff_summary")
    private String diffSummary;

    @Column(name = "error_type")
    private String errorType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getShadowTaskId() {
        return shadowTaskId;
    }

    public void setShadowTaskId(String shadowTaskId) {
        this.shadowTaskId = shadowTaskId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public String getRequestPayloadRedacted() {
        return requestPayloadRedacted;
    }

    public void setRequestPayloadRedacted(String requestPayloadRedacted) {
        this.requestPayloadRedacted = requestPayloadRedacted;
    }

    public String getPrimaryModel() {
        return primaryModel;
    }

    public void setPrimaryModel(String primaryModel) {
        this.primaryModel = primaryModel;
    }

    public String getCandidateModel() {
        return candidateModel;
    }

    public void setCandidateModel(String candidateModel) {
        this.candidateModel = candidateModel;
    }

    public Integer getPrimaryStatus() {
        return primaryStatus;
    }

    public void setPrimaryStatus(Integer primaryStatus) {
        this.primaryStatus = primaryStatus;
    }

    public Integer getCandidateStatus() {
        return candidateStatus;
    }

    public void setCandidateStatus(Integer candidateStatus) {
        this.candidateStatus = candidateStatus;
    }

    public Long getPrimaryLatencyMs() {
        return primaryLatencyMs;
    }

    public void setPrimaryLatencyMs(Long primaryLatencyMs) {
        this.primaryLatencyMs = primaryLatencyMs;
    }

    public Long getCandidateLatencyMs() {
        return candidateLatencyMs;
    }

    public void setCandidateLatencyMs(Long candidateLatencyMs) {
        this.candidateLatencyMs = candidateLatencyMs;
    }

    public String getPrimaryRawOutput() {
        return primaryRawOutput;
    }

    public void setPrimaryRawOutput(String primaryRawOutput) {
        this.primaryRawOutput = primaryRawOutput;
    }

    public String getCandidateRawOutput() {
        return candidateRawOutput;
    }

    public void setCandidateRawOutput(String candidateRawOutput) {
        this.candidateRawOutput = candidateRawOutput;
    }

    public String getPrimaryJsonOutput() {
        return primaryJsonOutput;
    }

    public void setPrimaryJsonOutput(String primaryJsonOutput) {
        this.primaryJsonOutput = primaryJsonOutput;
    }

    public String getCandidateJsonOutput() {
        return candidateJsonOutput;
    }

    public void setCandidateJsonOutput(String candidateJsonOutput) {
        this.candidateJsonOutput = candidateJsonOutput;
    }

    public String getDiffSummary() {
        return diffSummary;
    }

    public void setDiffSummary(String diffSummary) {
        this.diffSummary = diffSummary;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
