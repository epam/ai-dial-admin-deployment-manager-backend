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
        // deadline = 5 + (10 * 2) + 30 = 55
        var probe = new ProbeProperties(true, 5, 10, 3, 2, new HttpGetProbe("/health", 8080));
        assertThat(calculator.compute(probe)).isEqualTo("55s");
    }

    @Test
    void computesDeadline_withNullFieldsFallsBackToDefaults() {
        // deadline = 0 (default) + (10 (default) * 3 (default)) + 30 = 60
        var probe = new ProbeProperties(true, null, null, null, null, new HttpGetProbe("/health", 8080));
        assertThat(calculator.compute(probe)).isEqualTo("60s");
    }

    @Test
    void computesDeadline_withPartialFields() {
        // initialDelay=100, period=default(10), failureThreshold=50
        // deadline = 100 + (10 * 50) + 30 = 630
        var probe = new ProbeProperties(true, 100, null, null, 50, new HttpGetProbe("/health", 8080));
        assertThat(calculator.compute(probe)).isEqualTo("630s");
    }

    @Test
    void computesDeadline_forLargeModelScenario() {
        // initialDelay=0, period=30, failureThreshold=60 -> 30min probe window
        // deadline = 0 + (30 * 60) + 30 = 1830
        var probe = new ProbeProperties(true, 0, 30, 5, 60, new HttpGetProbe("/health", 8080));
        assertThat(calculator.compute(probe)).isEqualTo("1830s");
    }

    @Test
    void computesDeadline_withCustomDefaults() {
        var customCalculator = new ProgressDeadlineCalculator(10, 20, 5, 60);
        // All null -> uses custom defaults: 10 + (20 * 5) + 60 = 170
        var probe = new ProbeProperties(true, null, null, null, null, new HttpGetProbe("/health", 8080));
        assertThat(customCalculator.compute(probe)).isEqualTo("170s");
    }

    @Test
    void computesDeadline_withZeroBuffer() {
        var zeroBufferCalculator = new ProgressDeadlineCalculator(0, 10, 3, 0);
        // deadline = 5 + (10 * 2) + 0 = 25
        var probe = new ProbeProperties(true, 5, 10, 3, 2, new HttpGetProbe("/health", 8080));
        assertThat(zeroBufferCalculator.compute(probe)).isEqualTo("25s");
    }
}
