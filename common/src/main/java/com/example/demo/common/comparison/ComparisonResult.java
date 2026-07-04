package com.example.demo.common.comparison;

/**
 * Result of comparing a primary and candidate LLM output.
 */
public final class ComparisonResult {

    private final boolean matched;
    private final String comparisonMode;
    private final String diffSummary;

    private ComparisonResult(boolean matched, String comparisonMode, String diffSummary) {
        this.matched = matched;
        this.comparisonMode = comparisonMode;
        this.diffSummary = diffSummary;
    }

    public static ComparisonResult matched(String comparisonMode) {
        return new ComparisonResult(true, comparisonMode, null);
    }

    public static ComparisonResult mismatched(String comparisonMode, String diffSummary) {
        return new ComparisonResult(false, comparisonMode, diffSummary);
    }

    public boolean isMatched() {
        return matched;
    }

    public String getComparisonMode() {
        return comparisonMode;
    }

    public String getDiffSummary() {
        return diffSummary;
    }
}
