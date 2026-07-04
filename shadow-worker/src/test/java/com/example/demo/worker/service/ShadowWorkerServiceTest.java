package com.example.demo.worker.service;

import com.example.demo.common.comparison.ComparisonResult;
import com.example.demo.common.comparison.ResponseComparator;
import com.example.demo.common.dto.ShadowTaskDto;
import com.example.demo.common.json.JsonExtractionResult;
import com.example.demo.common.json.JsonExtractor;
import com.example.demo.common.logging.StructuredLogger;
import com.example.demo.worker.client.CandidateLlmClient;
import com.example.demo.worker.client.CandidateLlmResult;
import com.example.demo.worker.entity.LlmShadowMismatchEntity;
import com.example.demo.worker.kafka.RetryDlqPublisher;
import com.example.demo.worker.repository.LlmShadowMismatchRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShadowWorkerServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CandidateLlmClient candidateLlmClient;
    private JsonExtractor jsonExtractor;
    private ResponseComparator responseComparator;
    private LlmShadowMismatchRepository mismatchRepository;
    private RetryDlqPublisher retryDlqPublisher;
    private StructuredLogger structuredLogger;
    private ShadowWorkerService service;

    @BeforeEach
    void setUp() {
        candidateLlmClient = Mockito.mock(CandidateLlmClient.class);
        jsonExtractor = Mockito.mock(JsonExtractor.class);
        responseComparator = Mockito.mock(ResponseComparator.class);
        mismatchRepository = Mockito.mock(LlmShadowMismatchRepository.class);
        retryDlqPublisher = Mockito.mock(RetryDlqPublisher.class);
        structuredLogger = Mockito.mock(StructuredLogger.class);

        service = new ShadowWorkerService(candidateLlmClient, jsonExtractor, responseComparator,
                mismatchRepository, retryDlqPublisher, structuredLogger);
    }

    private ShadowTaskDto sampleTask() {
        return ShadowTaskDto.builder()
                .shadowTaskId("task-1")
                .correlationId("corr-1")
                .requestText("What is the capital of France?")
                .requestPayloadRedacted("What is the capital of France?")
                .requestHash("hash-1")
                .primaryResponsePayload("{\"answer\":\"Paris\"}")
                .primaryStatusCode(200)
                .primaryLatencyMs(100L)
                .primaryModel("primary-model")
                .candidateModel("candidate-model")
                .attempt(1)
                .maxAttempts(5)
                .build();
    }

    private JsonNode json(String text) throws Exception {
        return objectMapper.readTree(text);
    }

    @Test
    void skipsProcessingWhenShadowTaskAlreadyPersisted() {
        when(mismatchRepository.existsByShadowTaskId("task-1")).thenReturn(true);

        service.process(sampleTask());

        verify(candidateLlmClient, never()).call(anyString());
        verify(mismatchRepository, never()).save(any());
    }

    @Test
    void routesToRetryDlqPublisherWhenCandidateCallFails() {
        ShadowTaskDto task = sampleTask();
        when(mismatchRepository.existsByShadowTaskId("task-1")).thenReturn(false);
        when(candidateLlmClient.call(task.getRequestText()))
                .thenReturn(new CandidateLlmResult(0, null, 50L, "connection refused"));

        service.process(task);

        verify(retryDlqPublisher).handleFailure(task, "candidate_call_error", "connection refused");
        verify(mismatchRepository, never()).save(any());
    }

    @Test
    void persistsMatchEntityWithHundredPercentMatchSummaryWhenComparisonMatches() throws Exception {
        ShadowTaskDto task = sampleTask();
        when(mismatchRepository.existsByShadowTaskId("task-1")).thenReturn(false);
        when(candidateLlmClient.call(task.getRequestText()))
                .thenReturn(new CandidateLlmResult(200, "{\"answer\":\"Paris\"}", 90L, null));

        JsonExtractionResult primaryExtraction = JsonExtractionResult.success("{\"answer\":\"Paris\"}", json("{\"answer\":\"Paris\"}"));
        JsonExtractionResult candidateExtraction = JsonExtractionResult.success("{\"answer\":\"Paris\"}", json("{\"answer\":\"Paris\"}"));
        when(jsonExtractor.extract("{\"answer\":\"Paris\"}")).thenReturn(primaryExtraction, candidateExtraction);

        when(responseComparator.compare(primaryExtraction, candidateExtraction))
                .thenReturn(ComparisonResult.matched("NORMALIZED_JSON"));

        service.process(task);

        ArgumentCaptor<LlmShadowMismatchEntity> captor = ArgumentCaptor.forClass(LlmShadowMismatchEntity.class);
        verify(mismatchRepository).save(captor.capture());

        LlmShadowMismatchEntity entity = captor.getValue();
        assertThat(entity.getShadowTaskId()).isEqualTo("task-1");
        assertThat(entity.getCorrelationId()).isEqualTo("corr-1");
        assertThat(entity.getMatchStatus()).isEqualTo("MATCH");
        assertThat(entity.getDiffSummary()).isEqualTo("100% match");
        assertThat(entity.getErrorType()).isNull();
        assertThat(entity.getPrimaryModel()).isEqualTo("primary-model");
        assertThat(entity.getCandidateModel()).isEqualTo("candidate-model");
        assertThat(entity.getPrimaryStatus()).isEqualTo(200);
        assertThat(entity.getCandidateStatus()).isEqualTo(200);
        assertThat(entity.getCreatedAt()).isNotNull();

        verify(retryDlqPublisher, never()).handleFailure(any(), anyString(), anyString());
    }

    @Test
    void persistsMismatchEntityWithComparatorDiffSummaryWhenComparisonMismatches() throws Exception {
        ShadowTaskDto task = sampleTask();
        when(mismatchRepository.existsByShadowTaskId("task-1")).thenReturn(false);
        when(candidateLlmClient.call(task.getRequestText()))
                .thenReturn(new CandidateLlmResult(200, "{\"answer\":\"Berlin\"}", 90L, null));

        JsonExtractionResult primaryExtraction = JsonExtractionResult.success("{\"answer\":\"Paris\"}", json("{\"answer\":\"Paris\"}"));
        JsonExtractionResult candidateExtraction = JsonExtractionResult.success("{\"answer\":\"Berlin\"}", json("{\"answer\":\"Berlin\"}"));
        when(jsonExtractor.extract("{\"answer\":\"Paris\"}")).thenReturn(primaryExtraction);
        when(jsonExtractor.extract("{\"answer\":\"Berlin\"}")).thenReturn(candidateExtraction);

        when(responseComparator.compare(primaryExtraction, candidateExtraction))
                .thenReturn(ComparisonResult.mismatched("NORMALIZED_JSON", "Normalized JSON payloads differ"));

        service.process(task);

        ArgumentCaptor<LlmShadowMismatchEntity> captor = ArgumentCaptor.forClass(LlmShadowMismatchEntity.class);
        verify(mismatchRepository).save(captor.capture());

        LlmShadowMismatchEntity entity = captor.getValue();
        assertThat(entity.getMatchStatus()).isEqualTo("MISMATCH");
        assertThat(entity.getDiffSummary()).isEqualTo("Normalized JSON payloads differ");
        assertThat(entity.getPrimaryJsonOutput()).isEqualTo(json("{\"answer\":\"Paris\"}").toString());
        assertThat(entity.getCandidateJsonOutput()).isEqualTo(json("{\"answer\":\"Berlin\"}").toString());
    }

    @Test
    void marksErrorTypeAsDegradedWhenJsonExtractionFailsOnEitherSide() throws Exception {
        ShadowTaskDto task = sampleTask();
        when(mismatchRepository.existsByShadowTaskId("task-1")).thenReturn(false);
        when(candidateLlmClient.call(task.getRequestText()))
                .thenReturn(new CandidateLlmResult(200, "Paris is the capital.", 90L, null));

        JsonExtractionResult primaryExtraction = JsonExtractionResult.success("{\"answer\":\"Paris\"}", json("{\"answer\":\"Paris\"}"));
        JsonExtractionResult candidateExtraction = JsonExtractionResult.failure("Paris is the capital.", "NO_JSON_FOUND", "no json");
        when(jsonExtractor.extract("{\"answer\":\"Paris\"}")).thenReturn(primaryExtraction);
        when(jsonExtractor.extract("Paris is the capital.")).thenReturn(candidateExtraction);

        when(responseComparator.compare(primaryExtraction, candidateExtraction))
                .thenReturn(ComparisonResult.mismatched("NORMALIZED_TEXT", "Normalized raw text payloads differ"));

        service.process(task);

        ArgumentCaptor<LlmShadowMismatchEntity> captor = ArgumentCaptor.forClass(LlmShadowMismatchEntity.class);
        verify(mismatchRepository).save(captor.capture());

        LlmShadowMismatchEntity entity = captor.getValue();
        assertThat(entity.getErrorType()).isEqualTo("JSON_EXTRACTION_DEGRADED");
        assertThat(entity.getCandidateJsonOutput()).isNull();
    }

    @Test
    void doesNotRouteToRetryWhenSaveFailsWithDuplicateKey() throws Exception {
        ShadowTaskDto task = sampleTask();
        when(mismatchRepository.existsByShadowTaskId("task-1")).thenReturn(false);
        when(candidateLlmClient.call(task.getRequestText()))
                .thenReturn(new CandidateLlmResult(200, "{\"answer\":\"Paris\"}", 90L, null));

        JsonExtractionResult extraction = JsonExtractionResult.success("{\"answer\":\"Paris\"}", json("{\"answer\":\"Paris\"}"));
        when(jsonExtractor.extract(anyString())).thenReturn(extraction);
        when(responseComparator.compare(any(), any())).thenReturn(ComparisonResult.matched("NORMALIZED_JSON"));
        when(mismatchRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        service.process(task);

        verify(retryDlqPublisher, never()).handleFailure(any(), anyString(), anyString());
    }

    @Test
    void routesToRetryDlqPublisherWhenSaveFailsWithGenericException() throws Exception {
        ShadowTaskDto task = sampleTask();
        when(mismatchRepository.existsByShadowTaskId("task-1")).thenReturn(false);
        when(candidateLlmClient.call(task.getRequestText()))
                .thenReturn(new CandidateLlmResult(200, "{\"answer\":\"Paris\"}", 90L, null));

        JsonExtractionResult extraction = JsonExtractionResult.success("{\"answer\":\"Paris\"}", json("{\"answer\":\"Paris\"}"));
        when(jsonExtractor.extract(anyString())).thenReturn(extraction);
        when(responseComparator.compare(any(), any())).thenReturn(ComparisonResult.matched("NORMALIZED_JSON"));
        when(mismatchRepository.save(any())).thenThrow(new RuntimeException("connection lost"));

        service.process(task);

        verify(retryDlqPublisher).handleFailure(task, "db_write_error", "connection lost");
    }
}
