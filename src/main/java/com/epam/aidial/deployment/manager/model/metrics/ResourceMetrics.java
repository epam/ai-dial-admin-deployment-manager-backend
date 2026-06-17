package com.epam.aidial.deployment.manager.model.metrics;

import java.util.List;

/**
 * Resource block of the unified schema. Replica counts come from the existing pod listing;
 * per-pod usage comes from {@code metrics.k8s.io} and degrades independently when the
 * metrics-server is absent (the list is then empty with the reason recorded in availability).
 */
public record ResourceMetrics(int replicasTotal, int replicasReady, List<PodResourceUsage> pods) {
}
