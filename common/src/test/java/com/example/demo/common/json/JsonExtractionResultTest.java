package com.example.demo.common.json;

import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonExtractionResultTest {

    @Test
    void successFactoryPopulatesRawTextAndParsedJson() {
        TextNode node = new TextNode("value");

        JsonExtractionResult result = JsonExtractionResult.success("raw", node);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRawText()).isEqualTo("raw");
        assertThat(result.getParsedJson()).isEqualTo(node);
        assertThat(result.getErrorType()).isNull();
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    void failureFactoryPopulatesErrorDetailsAndLeavesParsedJsonNull() {
        JsonExtractionResult result = JsonExtractionResult.failure("raw text", "NO_JSON_FOUND", "nothing found");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getRawText()).isEqualTo("raw text");
        assertThat(result.getParsedJson()).isNull();
        assertThat(result.getErrorType()).isEqualTo("NO_JSON_FOUND");
        assertThat(result.getErrorMessage()).isEqualTo("nothing found");
    }
}
