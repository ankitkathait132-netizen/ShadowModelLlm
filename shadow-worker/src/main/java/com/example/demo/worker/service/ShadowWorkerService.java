package com.example.demo.worker.service;

import com.example.demo.common.comparison.ComparisonResult;
import com.example.demo.common.comparison.ResponseComparator;
import com.example.demo.common.dto.ShadowTaskDto;
import com.example.demo.common.json.JsonExtractionResult;
import com.example.demo.common.json.JsonExtractor;
import com.example.demo.common.logging.LogEvent;
import com.example.demo.common.logging.StructuredLogger;
import com.example.demo.worker.client.CandidateLlmClient;
import com.example.demo.worker.client.CandidateLlmResult;
import com.example.demo.worker.entity.LlmShadowMismatchEntity;
import com.example.demo.worker.kafka.RetryDlqPublisher;
import com.example.demo.worker.repository.LlmShadowMismatchRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Consumes a shadow task: calls the candidate LLM, extracts + compares JSON
 * against the primary output, and persists a mismatch row only when they differ.
 * Idempotent on {@code shadowTaskId} to tolerate Kafka redelivery.
 */
@Service
public class ShadowWorkerService {

    private static final String COMPONENT = "shadow-worker";

    private final CandidateLlmClient candidateLlmClient;
    private final JsonExtractor jsonExtractor;
    private final ResponseComparator responseComparator;
    private final LlmShadowMismatchRepository mismatchRepository;
    private final RetryDlqPublisher retryDlqPublisher;
    private final StructuredLogger structuredLogger;

    public ShadowWorkerService(CandidateLlmClient candidateLlmClient, JsonExtractor jsonExtractor,
                                ResponseComparator responseComparator, LlmShadowMismatchRepository mismatchRepository,
                                RetryDlqPublisher retryDlqPublisher, StructuredLogger structuredLogger) {
        this.candidateLlmClient = candidateLlmClient;
        this.jsonExtractor = jsonExtractor;
        this.responseComparator = responseComparator;
        this.mismatchRepository = mismatchRepository;
        this.retryDlqPublisher = retryDlqPublisher;
        this.structuredLogger = structuredLogger;
    }

    public void process(ShadowTaskDto task) {
        structuredLogger.log(LogEvent.builder("shadow.task.received")
                .component(COMPONENT)
                .operation("consume")
                .correlationId(task.getCorrelationId())
                .shadowTaskId(task.getShadowTaskId())
                .attempt(task.getAttempt())
                .status("received")
                .build());

        if (mismatchRepository.existsByShadowTaskId(task.getShadowTaskId())) {
            structuredLogger.log(LogEvent.builder("shadow.task.duplicate")
                    .component(COMPONENT)
                    .operation("consume")
                    .correlationId(task.getCorrelationId())
                    .shadowTaskId(task.getShadowTaskId())
                    .status("skipped")
                    .message("Mismatch already persisted for this shadow task; skipping duplicate delivery")
                    .build());
            return;
        }

        CandidateLlmResult candidateResult = candidateLlmClient.call(task.getRequestPayloadRedacted());

        if (!candidateResult.isSuccess()) {
            structuredLogger.log(LogEvent.builder("shadow.candidate.failed")
                    .component(COMPONENT)
                    .operation("candidate_call")
                    .correlationId(task.getCorrelationId())
                    .shadowTaskId(task.getShadowTaskId())
                    .attempt(task.getAttempt())
                    .status("failure")
                    .durationMs(candidateResult.getLatencyMs())
                    .errorType("candidate_call_error")
                    .message(candidateResult.getErrorMessage())
                    .build());
            retryDlqPublisher.handleFailure(task, "candidate_call_error", candidateResult.getErrorMessage());
            return;
        }

        structuredLogger.log(LogEvent.builder("shadow.candidate.completed")
                .component(COMPONENT)
                .operation("candidate_call")
                .correlationId(task.getCorrelationId())
                .shadowTaskId(task.getShadowTaskId())
                .attempt(task.getAttempt())
                .status("success")
                .durationMs(candidateResult.getLatencyMs())
                .candidateModel(task.getCandidateModel())
                .build());

        JsonExtractionResult primaryExtraction = jsonExtractor.extract(task.getPrimaryResponsePayload());
        JsonExtractionResult candidateExtraction = jsonExtractor.extract(candidateResult.getResponseBody());

        if (!primaryExtraction.isSuccess() || !candidateExtraction.isSuccess()) {
            structuredLogger.log(LogEvent.builder("shadow.json_extraction.failed")
                    .component(COMPONENT)
                    .operation("json_extraction")
                    .correlationId(task.getCorrelationId())
                    .shadowTaskId(task.getShadowTaskId())
                    .status("degraded")
                    .errorType(!primaryExtraction.isSuccess()
                            ? primaryExtraction.getErrorType() : candidateExtraction.getErrorType())
                    .message("Falling back to normalized raw text comparison")
                    .build());
        }

        ComparisonResult comparisonResult = responseComparator.compare(primaryExtraction, candidateExtraction);

        if (comparisonResult.isMatched()) {
            structuredLogger.log(LogEvent.builder("shadow.match")
                    .component(COMPONENT)
                    .operation("compare")
                    .correlationId(task.getCorrelationId())
                    .shadowTaskId(task.getShadowTaskId())
                    .status("matched")
                    .build());
            return;
        }

        persistMismatch(task, candidateResult, comparisonResult, primaryExtraction, candidateExtraction);
    }

    private void persistMismatch(ShadowTaskDto task, CandidateLlmResult candidateResult,
                                  ComparisonResult comparisonResult, JsonExtractionResult primaryExtraction,
                                  JsonExtractionResult candidateExtraction) {
        LlmShadowMismatchEntity entity = new LlmShadowMismatchEntity();
        entity.setShadowTaskId(task.getShadowTaskId());
        entity.setCorrelationId(task.getCorrelationId());
        entity.setRequestHash(task.getRequestHash());
        entity.setRequestPayloadRedacted(task.getRequestPayloadRedacted());
        entity.setPrimaryModel(task.getPrimaryModel());
        entity.setCandidateModel(task.getCandidateModel());
        entity.setPrimaryStatus(task.getPrimaryStatusCode());
        entity.setCandidateStatus(candidateResult.getStatusCode());
        entity.setPrimaryLatencyMs(task.getPrimaryLatencyMs());
        entity.setCandidateLatencyMs(candidateResult.getLatencyMs());
        entity.setPrimaryRawOutput(task.getPrimaryResponsePayload());
        entity.setCandidateRawOutput(candidateResult.getResponseBody());
        entity.setPrimaryJsonOutput(primaryExtraction.isSuccess() ? primaryExtraction.getParsedJson().toString() : null);
        entity.setCandidateJsonOutput(candidateExtraction.isSuccess() ? candidateExtraction.getParsedJson().toString() : null);
        entity.setDiffSummary(comparisonResult.getDiffSummary());
        entity.setErrorType(!primaryExtraction.isSuccess() || !candidateExtraction.isSuccess()
                ? "JSON_EXTRACTION_DEGRADED" : null);
        entity.setCreatedAt(Instant.now());

        try {
            mismatchRepository.save(entity);
            structuredLogger.log(LogEvent.builder("shadow.mismatch.persisted")
                    .component(COMPONENT)
                    .operation("db_write")
                    .correlationId(task.getCorrelationId())
                    .shadowTaskId(task.getShadowTaskId())
                    .status("success")
                    .message(comparisonResult.getDiffSummary())
                    .build());
        } catch (DataIntegrityViolationException duplicate) {
            structuredLogger.log(LogEvent.builder("shadow.mismatch.duplicate")
                    .component(COMPONENT)
                    .operation("db_write")
                    .correlationId(task.getCorrelationId())
                    .shadowTaskId(task.getShadowTaskId())
                    .status("skipped")
                    .message("Mismatch row already exists for this shadow task id")
                    .build());
        } catch (Exception e) {
            structuredLogger.log(LogEvent.builder("shadow.mismatch.persist_failed")
                    .component(COMPONENT)
                    .operation("db_write")
                    .correlationId(task.getCorrelationId())
                    .shadowTaskId(task.getShadowTaskId())
                    .status("failure")
                    .errorType("db_write_error")
                    .message(e.getMessage())
                    .build());
            retryDlqPublisher.handleFailure(task, "db_write_error", e.getMessage());
        }
    }
}
