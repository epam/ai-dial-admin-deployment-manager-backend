package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.model.probe.HttpGetProbe;
import com.epam.aidial.deployment.manager.model.probe.ProbeProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressDeadlineCalculatorTest {

    private final ProgressDeadlineCalculator calculator = new ProgressDeadlineCalculator(0, 10, 3, 30);

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
    void computesDeadline_withAllFieldsSet() {
        // deadline = 5 + ((2-1) * 10) + 3 + 30 = 48
        var probe = new ProbeProperties(true, 5, 10, 3, 2, new HttpGetProbe("/health", 8080));
        assertThat(calculator.compute(probe)).isEqualTo("48s");
    }

    @Test
    void computesDeadline_withNullFieldsFallsBackToDefaults() {
        // deadline = 0 + ((3-1) * 10) + 1 (default timeout) + 30 = 51
        var probe = new ProbeProperties(true, null, null, null, null, new HttpGetProbe("/health", 8080));
        assertThat(calculator.compute(probe)).isEqualTo("51s");
    }

    @Test
    void computesDeadline_withPartialFields() {
        // initialDelay=100, period=default(10), timeout=default(1), failureThreshold=50
        // deadline = 100 + ((50-1) * 10) + 1 + 30 = 621
        var probe = new ProbeProperties(true, 100, null, null, 50, new HttpGetProbe("/health", 8080));
        assertThat(calculator.compute(probe)).isEqualTo("621s");
    }

    @Test
    void computesDeadline_forLargeModelScenario() {
        // initialDelay=0, period=30, timeout=5, failureThreshold=60
        // deadline = 0 + ((60-1) * 30) + 5 + 30 = 1805
        var probe = new ProbeProperties(true, 0, 30, 5, 60, new HttpGetProbe("/health", 8080));
        assertThat(calculator.compute(probe)).isEqualTo("1805s");
    }

    @Test
    void computesDeadline_withCustomDefaults() {
        var customCalculator = new ProgressDeadlineCalculator(10, 20, 5, 60);
        // All null -> uses custom defaults: 10 + ((5-1) * 20) + 1 (default timeout) + 60 = 151
        var probe = new ProbeProperties(true, null, null, null, null, new HttpGetProbe("/health", 8080));
        assertThat(customCalculator.compute(probe)).isEqualTo("151s");
    }

    @Test
    void computesDeadline_withZeroBuffer() {
        var zeroBufferCalculator = new ProgressDeadlineCalculator(0, 10, 3, 0);
        // deadline = 5 + ((2-1) * 10) + 3 + 0 = 18
        var probe = new ProbeProperties(true, 5, 10, 3, 2, new HttpGetProbe("/health", 8080));
        assertThat(zeroBufferCalculator.compute(probe)).isEqualTo("18s");
    }
}
