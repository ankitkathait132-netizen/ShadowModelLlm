package com.example.demo.common.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonExtractorTest {

    private JsonExtractor jsonExtractor;

    @BeforeEach
    void setUp() {
        jsonExtractor = new JsonExtractor(new ObjectMapper());
    }

    @Test
    void extractsWhenEntireTextIsValidJson() {
        JsonExtractionResult result = jsonExtractor.extract("{\"answer\": \"Paris\"}");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getParsedJson().get("answer").asText()).isEqualTo("Paris");
    }

    @Test
    void extractsFirstBalancedJsonObjectEmbeddedInProseText() {
        String text = "Sure, here is the answer: {\"city\": \"Paris\", \"country\": \"France\"} Hope that helps!";

        JsonExtractionResult result = jsonExtractor.extract(text);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getParsedJson().get("city").asText()).isEqualTo("Paris");
        assertThat(result.getParsedJson().get("country").asText()).isEqualTo("France");
    }

    @Test
    void extractsFirstBalancedJsonArrayEmbeddedInProseText() {
        String text = "The values are [1, 2, 3] as computed.";

        JsonExtractionResult result = jsonExtractor.extract(text);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getParsedJson().isArray()).isTrue();
        assertThat(result.getParsedJson().size()).isEqualTo(3);
    }

    @Test
    void handlesNestedBracesWithinEmbeddedJson() {
        String text = "Result: {\"outer\": {\"inner\": \"value\"}, \"list\": [1, 2]} done.";

        JsonExtractionResult result = jsonExtractor.extract(text);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getParsedJson().get("outer").get("inner").asText()).isEqualTo("value");
    }

    @Test
    void ignoresBracesInsideStringLiteralsWhenBalancing() {
        String text = "Text with a brace in a string {\"note\": \"contains } inside string\", \"ok\": true}";

        JsonExtractionResult result = jsonExtractor.extract(text);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getParsedJson().get("ok").asBoolean()).isTrue();
    }

    @Test
    void returnsFailureWhenNoJsonPresent() {
        JsonExtractionResult result = jsonExtractor.extract("The capital of France is Paris.");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorType()).isEqualTo("NO_JSON_FOUND");
        assertThat(result.getRawText()).isEqualTo("The capital of France is Paris.");
    }

    @Test
    void returnsFailureForUnbalancedJsonBlock() {
        JsonExtractionResult result = jsonExtractor.extract("Here you go: {\"unterminated\": \"value\"");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorType()).isEqualTo("NO_JSON_FOUND");
    }

    @Test
    void returnsFailureForNullInput() {
        JsonExtractionResult result = jsonExtractor.extract(null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorType()).isEqualTo("EMPTY_INPUT");
    }

    @Test
    void returnsFailureForBlankInput() {
        JsonExtractionResult result = jsonExtractor.extract("   ");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorType()).isEqualTo("EMPTY_INPUT");
    }
}
