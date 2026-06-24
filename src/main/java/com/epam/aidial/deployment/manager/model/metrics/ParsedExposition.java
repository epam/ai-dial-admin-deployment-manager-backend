package com.epam.aidial.deployment.manager.model.metrics;

import java.util.List;
import java.util.Set;

/**
 * Result of parsing a Prometheus text exposition: the value-bearing {@code samples} plus the set of
 * metric base names {@code declaredMetricNames} declared in {@code # TYPE} lines. The declared names
 * matter for engine detection of label-gated histograms (e.g. the KServe Python ModelServer's
 * {@code request_predict_seconds}) which emit no sample lines until the model serves its first
 * request — only their {@code # TYPE} metadata is present while idle.
 */
public record ParsedExposition(List<MetricSample> samples, Set<String> declaredMetricNames) {
}
