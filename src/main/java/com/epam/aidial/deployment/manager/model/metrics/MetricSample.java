package com.epam.aidial.deployment.manager.model.metrics;

import java.util.Map;

/**
 * A single parsed Prometheus exposition-format sample line.
 * Histogram series arrive as related samples ({@code *_bucket} with an {@code le} label,
 * {@code *_sum}, {@code *_count}); grouping them is the consumers' concern.
 */
public record MetricSample(String name, Map<String, String> labels, double value) {
}
