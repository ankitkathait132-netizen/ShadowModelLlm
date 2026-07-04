package com.example.demo.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestHasherTest {

    @Test
    void sha256HexProducesKnownDigestForEmptyString() {
        assertThat(RequestHasher.sha256Hex(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void sha256HexProducesKnownDigestForSampleText() {
        assertThat(RequestHasher.sha256Hex("hello"))
                .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    void sha256HexIsDeterministic() {
        String first = RequestHasher.sha256Hex("What is the capital of France?");
        String second = RequestHasher.sha256Hex("What is the capital of France?");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void sha256HexDiffersForDifferentInput() {
        String first = RequestHasher.sha256Hex("input-one");
        String second = RequestHasher.sha256Hex("input-two");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void sha256HexReturnsLowercaseHexOfExpectedLength() {
        String hash = RequestHasher.sha256Hex("some arbitrary text");

        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
    }
}
