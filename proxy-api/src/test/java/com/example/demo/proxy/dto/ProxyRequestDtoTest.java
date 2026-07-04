package com.example.demo.proxy.dto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors the production {@code spring.jackson.deserialization.fail-on-unknown-properties: true}
 * setting from application.yml, which is required for {@code @JsonIgnoreProperties(ignoreUnknown = false)}
 * on {@link ProxyRequestDto} to actually reject unexpected fields.
 */
class ProxyRequestDtoTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    @Test
    void deserializesTextAndCorrelationId() throws Exception {
        String json = "{\"text\": \"What is the capital of France?\", \"correlationId\": \"abc-123\"}";

        ProxyRequestDto dto = objectMapper.readValue(json, ProxyRequestDto.class);

        assertThat(dto.getText()).isEqualTo("What is the capital of France?");
        assertThat(dto.getCorrelationId()).isEqualTo("abc-123");
    }

    @Test
    void correlationIdIsOptional() throws Exception {
        String json = "{\"text\": \"hello\"}";

        ProxyRequestDto dto = objectMapper.readValue(json, ProxyRequestDto.class);

        assertThat(dto.getText()).isEqualTo("hello");
        assertThat(dto.getCorrelationId()).isNull();
    }

    @Test
    void rejectsUnknownFieldLikePrimaryModel() {
        String json = "{\"text\": \"hello\", \"primaryModel\": \"mistral-7b-instruct-v0.3\"}";

        assertThatThrownBy(() -> objectMapper.readValue(json, ProxyRequestDto.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }

    @Test
    void rejectsUnknownFieldLikeCandidateModel() {
        String json = "{\"text\": \"hello\", \"candidateModel\": \"llama3-8b-instruct\"}";

        assertThatThrownBy(() -> objectMapper.readValue(json, ProxyRequestDto.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }

    @Test
    void rejectsUnknownFieldLikeRawPayload() {
        String json = "{\"text\": \"hello\", \"payload\": {\"foo\": \"bar\"}}";

        assertThatThrownBy(() -> objectMapper.readValue(json, ProxyRequestDto.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }
}
