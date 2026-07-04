package com.example.demo.common.comparison;

import com.example.demo.common.json.JsonExtractionResult;
import org.springframework.stereotype.Component;

/**
 * Compares primary and candidate LLM outputs.
 *
 * Prefers normalized JSON equality when both outputs parsed successfully, and falls
 * back to normalized raw text comparison when JSON extraction failed for either side.
 */
@Component
public class ResponseComparator {

    private static final String MODE_NORMALIZED_JSON = "NORMALIZED_JSON";
    private static final String MODE_NORMALIZED_TEXT = "NORMALIZED_TEXT";

    public ComparisonResult compare(JsonExtractionResult primary, JsonExtractionResult candidate) {
        if (primary.isSuccess() && candidate.isSuccess()) {
            boolean matched = primary.getParsedJson().equals(candidate.getParsedJson());
            return matched
                    ? ComparisonResult.matched(MODE_NORMALIZED_JSON)
                    : ComparisonResult.mismatched(MODE_NORMALIZED_JSON, "Normalized JSON payloads differ");
        }

        String normalizedPrimary = normalize(primary.getRawText());
        String normalizedCandidate = normalize(candidate.getRawText());
        boolean matched = normalizedPrimary.equals(normalizedCandidate);
        return matched
                ? ComparisonResult.matched(MODE_NORMALIZED_TEXT)
                : ComparisonResult.mismatched(MODE_NORMALIZED_TEXT, "Normalized raw text payloads differ");
    }

    private String normalize(String text) {
        return text == null ? "" : text.strip().replaceAll("\\s+", " ");
    }
}
