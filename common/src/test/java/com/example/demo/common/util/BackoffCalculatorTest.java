package com.example.demo.common.util;

import com.example.demo.common.config.ShadowProxyProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BackoffCalculatorTest {

    private ShadowProxyProperties.Retry retryConfig(long initialBackoffMs, double multiplier,
                                                      long maxBackoffMs, boolean jitterEnabled) {
        ShadowProxyProperties.Retry retry = new ShadowProxyProperties.Retry();
        retry.setInitialBackoffMs(initialBackoffMs);
        retry.setBackoffMultiplier(multiplier);
        retry.setMaxBackoffMs(maxBackoffMs);
        retry.setJitterEnabled(jitterEnabled);
        return retry;
    }

    @Test
    void firstAttemptWithoutJitterReturnsInitialBackoff() {
        ShadowProxyProperties.Retry retry = retryConfig(500, 2.0, 30000, false);

        long delay = BackoffCalculator.computeDelayMs(retry, 1);

        assertThat(delay).isEqualTo(500);
    }

    @Test
    void delayGrowsExponentiallyWithoutJitter() {
        ShadowProxyProperties.Retry retry = retryConfig(500, 2.0, 30000, false);

        assertThat(BackoffCalculator.computeDelayMs(retry, 1)).isEqualTo(500);
        assertThat(BackoffCalculator.computeDelayMs(retry, 2)).isEqualTo(1000);
        assertThat(BackoffCalculator.computeDelayMs(retry, 3)).isEqualTo(2000);
        assertThat(BackoffCalculator.computeDelayMs(retry, 4)).isEqualTo(4000);
    }

    @Test
    void delayIsCappedAtMaxBackoff() {
        ShadowProxyProperties.Retry retry = retryConfig(500, 2.0, 1500, false);

        long delay = BackoffCalculator.computeDelayMs(retry, 10);

        assertThat(delay).isEqualTo(1500);
    }

    @Test
    void attemptZeroOrNegativeIsTreatedLikeFirstAttempt() {
        ShadowProxyProperties.Retry retry = retryConfig(500, 2.0, 30000, false);

        assertThat(BackoffCalculator.computeDelayMs(retry, 0)).isEqualTo(500);
        assertThat(BackoffCalculator.computeDelayMs(retry, -5)).isEqualTo(500);
    }

    @Test
    void jitterEnabledReturnsValueWithinBounds() {
        ShadowProxyProperties.Retry retry = retryConfig(500, 2.0, 30000, true);

        for (int i = 0; i < 50; i++) {
            long delay = BackoffCalculator.computeDelayMs(retry, 3);
            assertThat(delay).isBetween(0L, 2000L);
        }
    }

    @Test
    void zeroCappedDelayReturnsZeroRegardlessOfJitter() {
        ShadowProxyProperties.Retry retry = retryConfig(0, 2.0, 0, true);

        long delay = BackoffCalculator.computeDelayMs(retry, 1);

        assertThat(delay).isEqualTo(0);
    }
}
