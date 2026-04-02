package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.model.probe.HttpGetProbe;
import com.epam.aidial.deployment.manager.model.probe.ProbeProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressDeadlineCalculatorTest {

    private static final int FALLBACK_TIMEOUT = 3600;

    private final ProgressDeadlineCalculator calculator = new ProgressDeadlineCalculator(0, 10, 3, 30);

    // --- compute(probe) overload: returns null when no probe ---

    @Test
    void returnsNull_whenProbeIsNull() {
        assertThat(calculator.compute(null)).isNull();
    }

    @Test
    void returnsNull_whenProbeIsDisabled() {
        var probe = new ProbeProperties(false, 10, 20, 5, 10, new HttpGetProbe("/health", 8080));
        assertThat(calculator.compute(probe)).isNull();
    }

    @Test
    void computesDeadline_withProbeOnly() {
        // deadline = 5 + ((2-1) * 10) + 3 + 30 = 48
        var probe = new ProbeProperties(true, 5, 10, 3, 2, new HttpGetProbe("/health", 8080));
        assertThat(calculator.compute(probe)).isEqualTo("48s");
    }

    // --- compute(probe, fallbackTimeoutSeconds) overload: returns fallback when no probe ---

    @Test
    void returnsFallbackPlusBuffer_whenProbeIsNull() {
        // deadline = 3600 + 30 = 3630
        assertThat(calculator.compute(null, FALLBACK_TIMEOUT)).isEqualTo("3630s");
    }

    @Test
    void returnsFallbackPlusBuffer_whenProbeIsDisabled() {
        var probe = new ProbeProperties(false, 10, 20, 5, 10, new HttpGetProbe("/health", 8080));
        // deadline = 3600 + 30 = 3630
        assertThat(calculator.compute(probe, FALLBACK_TIMEOUT)).isEqualTo("3630s");
    }

    @Test
    void returnsFallbackPlusBuffer_withSmallFallback() {
        // deadline = 60 + 30 = 90
        assertThat(calculator.compute(null, 60)).isEqualTo("90s");
    }

    @Test
    void computesDeadline_withAllFieldsSet() {
        // deadline = 5 + ((2-1) * 10) + 3 + 30 = 48
        var probe = new ProbeProperties(true, 5, 10, 3, 2, new HttpGetProbe("/health", 8080));
        assertThat(calculator.compute(probe, FALLBACK_TIMEOUT)).isEqualTo("48s");
    }

    @Test
    void computesDeadline_withNullFieldsFallsBackToDefaults() {
        // deadline = 0 + ((3-1) * 10) + 1 (default timeout) + 30 = 51
        var probe = new ProbeProperties(true, null, null, null, null, new HttpGetProbe("/health", 8080));
        assertThat(calculator.compute(probe, FALLBACK_TIMEOUT)).isEqualTo("51s");
    }

    @Test
    void computesDeadline_withPartialFields() {
        // initialDelay=100, period=default(10), timeout=default(1), failureThreshold=50
        // deadline = 100 + ((50-1) * 10) + 1 + 30 = 621
        var probe = new ProbeProperties(true, 100, null, null, 50, new HttpGetProbe("/health", 8080));
        assertThat(calculator.compute(probe, FALLBACK_TIMEOUT)).isEqualTo("621s");
    }

    @Test
    void computesDeadline_forLargeModelScenario() {
        // initialDelay=0, period=30, timeout=5, failureThreshold=60
        // deadline = 0 + ((60-1) * 30) + 5 + 30 = 1805
        var probe = new ProbeProperties(true, 0, 30, 5, 60, new HttpGetProbe("/health", 8080));
        assertThat(calculator.compute(probe, FALLBACK_TIMEOUT)).isEqualTo("1805s");
    }

    @Test
    void computesDeadline_withCustomDefaults() {
        var customCalculator = new ProgressDeadlineCalculator(10, 20, 5, 60);
        // All null -> uses custom defaults: 10 + ((5-1) * 20) + 1 (default timeout) + 60 = 151
        var probe = new ProbeProperties(true, null, null, null, null, new HttpGetProbe("/health", 8080));
        assertThat(customCalculator.compute(probe, FALLBACK_TIMEOUT)).isEqualTo("151s");
    }

    @Test
    void computesDeadline_withZeroBuffer() {
        var zeroBufferCalculator = new ProgressDeadlineCalculator(0, 10, 3, 0);
        // deadline = 5 + ((2-1) * 10) + 3 + 0 = 18
        var probe = new ProbeProperties(true, 5, 10, 3, 2, new HttpGetProbe("/health", 8080));
        assertThat(zeroBufferCalculator.compute(probe, FALLBACK_TIMEOUT)).isEqualTo("18s");
    }

    @Test
    void returnsFallbackPlusBuffer_withZeroBuffer_whenProbeIsNull() {
        var zeroBufferCalculator = new ProgressDeadlineCalculator(0, 10, 3, 0);
        // deadline = 3600 + 0 = 3600
        assertThat(zeroBufferCalculator.compute(null, FALLBACK_TIMEOUT)).isEqualTo("3600s");
    }
}
