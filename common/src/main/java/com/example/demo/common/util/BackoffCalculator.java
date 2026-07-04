package com.example.demo.common.util;

import com.example.demo.common.config.ShadowProxyProperties;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Computes retry backoff delays from {@link ShadowProxyProperties.Retry} config:
 * exponential growth from {@code initialBackoffMs}, capped at {@code maxBackoffMs},
 * with optional full jitter.
 */
public final class BackoffCalculator {

    private BackoffCalculator() {
    }

    public static long computeDelayMs(ShadowProxyProperties.Retry retryConfig, int attempt) {
        double exponential = retryConfig.getInitialBackoffMs()
                * Math.pow(retryConfig.getBackoffMultiplier(), Math.max(0, attempt - 1));
        long capped = Math.min((long) exponential, retryConfig.getMaxBackoffMs());

        if (!retryConfig.isJitterEnabled() || capped <= 0) {
            return capped;
        }
        return ThreadLocalRandom.current().nextLong(0, capped + 1);
    }
}
