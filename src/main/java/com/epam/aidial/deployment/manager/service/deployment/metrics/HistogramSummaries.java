package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.model.metrics.DistributionSummary;
import com.epam.aidial.deployment.manager.model.metrics.MetricSample;

import java.util.List;
import java.util.TreeMap;

/**
 * Builds {@link DistributionSummary} values from cumulative Prometheus histogram series
 * in a single snapshot: mean = {@code _sum/_count}; p50/p95/p99 are approximated by linear
 * interpolation over cumulative {@code le} buckets, clamped at the last finite bucket
 * boundary when the target rank falls into the {@code +Inf} bucket.
 */
final class HistogramSummaries {

    private HistogramSummaries() {
    }

    /**
     * Summarizes the histogram with the given base name (its series are {@code base_bucket},
     * {@code base_sum}, {@code base_count}). Multiple label sets (e.g. per-model labels) are
     * aggregated by summation. Returns {@code null} when the histogram is absent or has no
     * observations.
     */
    static DistributionSummary summarize(List<MetricSample> samples, String baseName) {
        var buckets = new TreeMap<Double, Double>();
        double sum = 0;
        double count = 0;
        boolean seen = false;

        for (var sample : samples) {
            if (sample.name().equals(baseName + "_bucket")) {
                var le = sample.labels().get("le");
                if (le != null) {
                    double bound = parseBound(le);
                    buckets.merge(bound, sample.value(), Double::sum);
                    seen = true;
                }
            } else if (sample.name().equals(baseName + "_sum")) {
                sum += sample.value();
                seen = true;
            } else if (sample.name().equals(baseName + "_count")) {
                count += sample.value();
                seen = true;
            }
        }

        if (!seen || count <= 0) {
            return null;
        }

        var mean = Double.isNaN(sum) ? null : sum / count;
        return new DistributionSummary(
                mean,
                percentile(buckets, count, 0.50),
                percentile(buckets, count, 0.95),
                percentile(buckets, count, 0.99),
                (long) count);
    }

    private static double parseBound(String le) {
        return switch (le) {
            case "+Inf", "Inf" -> Double.POSITIVE_INFINITY;
            default -> Double.parseDouble(le);
        };
    }

    private static Double percentile(TreeMap<Double, Double> buckets, double count, double quantile) {
        if (buckets.isEmpty()) {
            return null;
        }
        double target = quantile * count;
        double previousBound = 0;
        double previousCumulative = 0;
        Double lastFiniteBound = null;

        for (var entry : buckets.entrySet()) {
            double bound = entry.getKey();
            double cumulative = entry.getValue();
            if (Double.isFinite(bound)) {
                lastFiniteBound = bound;
            }
            if (cumulative >= target) {
                if (!Double.isFinite(bound)) {
                    // Target rank falls into the +Inf bucket — clamp to the last finite boundary.
                    return lastFiniteBound;
                }
                double bucketCount = cumulative - previousCumulative;
                if (bucketCount <= 0) {
                    return bound;
                }
                double fraction = (target - previousCumulative) / bucketCount;
                return previousBound + fraction * (bound - previousBound);
            }
            previousBound = Double.isFinite(bound) ? bound : previousBound;
            previousCumulative = cumulative;
        }
        return lastFiniteBound;
    }

}
