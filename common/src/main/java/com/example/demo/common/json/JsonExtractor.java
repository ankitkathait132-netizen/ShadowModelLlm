package com.example.demo.common.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Extracts structured JSON from raw LLM text output.
 *
 * Extraction order:
 * 1. Try parsing the full response as JSON.
 * 2. If that fails, scan for the first balanced JSON object or array in the text.
 * 3. Parse the extracted block with Jackson.
 * 4. If nothing parses, return a structured failure so callers can fall back to raw text comparison.
 */
@Component
public class JsonExtractor {

    private final ObjectMapper objectMapper;

    public JsonExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonExtractionResult extract(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return JsonExtractionResult.failure(rawText, "EMPTY_INPUT", "Input text is empty");
        }

        try {
            JsonNode node = objectMapper.readTree(rawText);
            return JsonExtractionResult.success(rawText, node);
        } catch (JsonProcessingException fullParseError) {
            String candidateBlock = findFirstBalancedJsonBlock(rawText);
            if (candidateBlock == null) {
                return JsonExtractionResult.failure(rawText, "NO_JSON_FOUND",
                        "No balanced JSON object or array found in text");
            }
            try {
                JsonNode node = objectMapper.readTree(candidateBlock);
                return JsonExtractionResult.success(rawText, node);
            } catch (JsonProcessingException blockParseError) {
                return JsonExtractionResult.failure(rawText, "JSON_PARSE_ERROR", blockParseError.getMessage());
            }
        }
    }

    private String findFirstBalancedJsonBlock(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{' || c == '[') {
                String block = extractBalancedBlock(text, i);
                if (block != null) {
                    return block;
                }
            }
        }
        return null;
    }

    private String extractBalancedBlock(String text, int startIndex) {
        char open = text.charAt(startIndex);
        char close = open == '{' ? '}' : ']';
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = startIndex; i < text.length(); i++) {
            char c = text.charAt(i);

            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                inString = true;
            } else if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return text.substring(startIndex, i + 1);
                }
            }
        }
        return null;
    }
}
