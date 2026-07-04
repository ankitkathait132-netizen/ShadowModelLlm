package com.example.demo.common.comparison;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ComparisonResultTest {

    @Test
    void matchedFactoryHasNoDiffSummary() {
        ComparisonResult result = ComparisonResult.matched("NORMALIZED_JSON");

        assertThat(result.isMatched()).isTrue();
        assertThat(result.getComparisonMode()).isEqualTo("NORMALIZED_JSON");
        assertThat(result.getDiffSummary()).isNull();
    }

    @Test
    void mismatchedFactoryCarriesDiffSummary() {
        ComparisonResult result = ComparisonResult.mismatched("NORMALIZED_TEXT", "payloads differ");

        assertThat(result.isMatched()).isFalse();
        assertThat(result.getComparisonMode()).isEqualTo("NORMALIZED_TEXT");
        assertThat(result.getDiffSummary()).isEqualTo("payloads differ");
    }
}
