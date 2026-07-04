package com.example.demo.worker.repository;

import com.example.demo.worker.entity.LlmShadowMismatchEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LlmShadowMismatchRepositoryTest {

    @Autowired
    private LlmShadowMismatchRepository repository;

    private LlmShadowMismatchEntity entityFor(String shadowTaskId, String matchStatus) {
        LlmShadowMismatchEntity entity = new LlmShadowMismatchEntity();
        entity.setShadowTaskId(shadowTaskId);
        entity.setCorrelationId("corr-" + shadowTaskId);
        entity.setRequestHash("hash-" + shadowTaskId);
        entity.setRequestPayloadRedacted("What is the capital of France?");
        entity.setPrimaryModel("primary-model");
        entity.setCandidateModel("candidate-model");
        entity.setMatchStatus(matchStatus);
        entity.setPrimaryStatus(200);
        entity.setCandidateStatus(200);
        entity.setPrimaryLatencyMs(100L);
        entity.setCandidateLatencyMs(120L);
        entity.setPrimaryRawOutput("{\"answer\":\"Paris\"}");
        entity.setCandidateRawOutput("{\"answer\":\"Paris\"}");
        entity.setPrimaryJsonOutput("{\"answer\":\"Paris\"}");
        entity.setCandidateJsonOutput("{\"answer\":\"Paris\"}");
        entity.setDiffSummary("100% match".equals(matchStatus) ? "100% match" : null);
        entity.setCreatedAt(Instant.now());
        return entity;
    }

    @Test
    void savesAndReloadsEntityWithAllFields() {
        LlmShadowMismatchEntity saved = repository.save(entityFor("task-1", "MATCH"));

        LlmShadowMismatchEntity reloaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getShadowTaskId()).isEqualTo("task-1");
        assertThat(reloaded.getCorrelationId()).isEqualTo("corr-task-1");
        assertThat(reloaded.getMatchStatus()).isEqualTo("MATCH");
        assertThat(reloaded.getPrimaryJsonOutput()).isEqualTo("{\"answer\":\"Paris\"}");
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    @Test
    void existsByShadowTaskIdReturnsTrueOnlyAfterPersisting() {
        assertThat(repository.existsByShadowTaskId("task-2")).isFalse();

        repository.save(entityFor("task-2", "MISMATCH"));

        assertThat(repository.existsByShadowTaskId("task-2")).isTrue();
    }

    @Test
    void rejectsDuplicateShadowTaskIdDueToUniqueConstraint() {
        repository.saveAndFlush(entityFor("task-3", "MATCH"));

        assertThatThrownBy(() -> repository.saveAndFlush(entityFor("task-3", "MISMATCH")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
