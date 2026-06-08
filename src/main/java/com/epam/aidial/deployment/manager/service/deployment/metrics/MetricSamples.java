package com.epam.aidial.deployment.manager.service.deployment.metrics;

import java.time.Instant;
import java.util.Optional;

/**
 * Shared extraction helpers for normalizers, backed by a {@link MetricSampleIndex}: name lookups
 * aggregate across label sets (e.g. per-model labels) by summation; lifetime rates use the
 * conventional {@code process_start_time_seconds} gauge when the engine exposes it.
 */
final class MetricSamples {

    /** Exposed by Prometheus client libraries' process collectors (epoch seconds). */
    static final String PROCESS_START_TIME_SECONDS = "process_start_time_seconds";

    private MetricSamples() {
    }

    /** Sum of all finite samples with any of the given names; empty when none are present. */
    static Optional<Double> sum(MetricSampleIndex index, String... names) {
        double total = 0;
        boolean seen = false;
        for (var name : names) {
            for (var sample : index.named(name)) {
                if (!Double.isNaN(sample.value())) {
                    total += sample.value();
                    seen = true;
                }
            }
        }
        return seen ? Optional.of(total) : Optional.empty();
    }

    /** Sum of samples with the given name whose label matches the given value. */
    static Optional<Double> sumWithLabel(MetricSampleIndex index, String name, String labelKey, String labelValue) {
        double total = 0;
        boolean seen = false;
        for (var sample : index.named(name)) {
            if (labelValue.equals(sample.labels().get(labelKey)) && !Double.isNaN(sample.value())) {
                total += sample.value();
                seen = true;
            }
        }
        return seen ? Optional.of(total) : Optional.empty();
    }

    static Integer asInteger(Optional<Double> value) {
        return value.map(v -> (int) Math.round(v)).orElse(null);
    }

    /**
     * Lifetime rate of a cumulative counter: counter value divided by engine process uptime.
     * Empty when the counter or {@code process_start_time_seconds} is absent — the raw counter
     * is still echoed so clients can derive rates themselves.
     */
    static Optional<Double> lifetimeRate(MetricSampleIndex index, String counterName, Instant now) {
        var counter = sum(index, counterName);
        var startTime = sum(index, PROCESS_START_TIME_SECONDS);
        if (counter.isEmpty() || startTime.isEmpty()) {
            return Optional.empty();
        }
        double uptimeSeconds = now.getEpochSecond() - startTime.get();
        if (uptimeSeconds <= 0) {
            return Optional.empty();
        }
        return Optional.of(counter.get() / uptimeSeconds);
    }

    /** Clamps a ratio to [0, 1]. */
    static double clampRatio(double value) {
        return Math.clamp(value, 0, 1);
    }

}
