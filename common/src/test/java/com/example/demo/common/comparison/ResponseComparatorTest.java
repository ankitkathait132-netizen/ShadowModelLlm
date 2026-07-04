package com.example.demo.common.comparison;

import com.example.demo.common.json.JsonExtractionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseComparatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ResponseComparator comparator = new ResponseComparator();

    private JsonNode json(String text) throws Exception {
        return objectMapper.readTree(text);
    }

    @Test
    void identicalJsonPayloadsAreMatchedUsingNormalizedJsonMode() throws Exception {
        JsonExtractionResult primary = JsonExtractionResult.success("{\"a\":1}", json("{\"a\":1}"));
        JsonExtractionResult candidate = JsonExtractionResult.success("{\"a\": 1}", json("{\"a\": 1}"));

        ComparisonResult result = comparator.compare(primary, candidate);

        assertThat(result.isMatched()).isTrue();
        assertThat(result.getComparisonMode()).isEqualTo("NORMALIZED_JSON");
        assertThat(result.getDiffSummary()).isNull();
    }

    @Test
    void differingJsonPayloadsAreMismatchedUsingNormalizedJsonMode() throws Exception {
        JsonExtractionResult primary = JsonExtractionResult.success("{\"a\":1}", json("{\"a\":1}"));
        JsonExtractionResult candidate = JsonExtractionResult.success("{\"a\":2}", json("{\"a\":2}"));

        ComparisonResult result = comparator.compare(primary, candidate);

        assertThat(result.isMatched()).isFalse();
        assertThat(result.getComparisonMode()).isEqualTo("NORMALIZED_JSON");
        assertThat(result.getDiffSummary()).isEqualTo("Normalized JSON payloads differ");
    }

    @Test
    void fallsBackToTextComparisonWhenPrimaryExtractionFailed() {
        JsonExtractionResult primary = JsonExtractionResult.failure("Paris is the capital.", "NO_JSON_FOUND", "no json");
        JsonExtractionResult candidate = JsonExtractionResult.failure("Paris is the capital.", "NO_JSON_FOUND", "no json");

        ComparisonResult result = comparator.compare(primary, candidate);

        assertThat(result.isMatched()).isTrue();
        assertThat(result.getComparisonMode()).isEqualTo("NORMALIZED_TEXT");
    }

    @Test
    void fallsBackToTextComparisonWhenCandidateExtractionFailed() throws Exception {
        JsonExtractionResult primary = JsonExtractionResult.success("{\"a\":1}", json("{\"a\":1}"));
        JsonExtractionResult candidate = JsonExtractionResult.failure("not json at all", "NO_JSON_FOUND", "no json");

        ComparisonResult result = comparator.compare(primary, candidate);

        assertThat(result.getComparisonMode()).isEqualTo("NORMALIZED_TEXT");
        assertThat(result.isMatched()).isFalse();
    }

    @Test
    void textComparisonNormalizesWhitespaceBeforeComparing() {
        JsonExtractionResult primary = JsonExtractionResult.failure("  Paris   is   the capital.  ", "NO_JSON_FOUND", "no json");
        JsonExtractionResult candidate = JsonExtractionResult.failure("Paris is the capital.", "NO_JSON_FOUND", "no json");

        ComparisonResult result = comparator.compare(primary, candidate);

        assertThat(result.isMatched()).isTrue();
        assertThat(result.getComparisonMode()).isEqualTo("NORMALIZED_TEXT");
    }

    @Test
    void textComparisonTreatsDifferingTextAsMismatch() {
        JsonExtractionResult primary = JsonExtractionResult.failure("Paris is the capital.", "NO_JSON_FOUND", "no json");
        JsonExtractionResult candidate = JsonExtractionResult.failure("Berlin is the capital.", "NO_JSON_FOUND", "no json");

        ComparisonResult result = comparator.compare(primary, candidate);

        assertThat(result.isMatched()).isFalse();
        assertThat(result.getDiffSummary()).isEqualTo("Normalized raw text payloads differ");
    }

    @Test
    void textComparisonTreatsNullRawTextAsEmptyString() {
        JsonExtractionResult primary = JsonExtractionResult.failure(null, "EMPTY_INPUT", "empty");
        JsonExtractionResult candidate = JsonExtractionResult.failure(null, "EMPTY_INPUT", "empty");

        ComparisonResult result = comparator.compare(primary, candidate);

        assertThat(result.isMatched()).isTrue();
    }
}
