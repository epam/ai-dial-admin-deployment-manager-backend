package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.model.metrics.MetricSample;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One-pass name index over parsed Prometheus samples. Built once per scrape so each normalizer
 * lookup (counter sum, histogram series, lifetime rate) hits a pre-grouped bucket instead of
 * rescanning the full sample list — a normalizer makes ~5–9 lookups, and a vLLM exposition is
 * ~500 lines. Lookups still aggregate across label sets (per-model labels) by summation; only
 * the iteration scope shrinks, so results are identical to scanning the whole list.
 */
final class MetricSampleIndex {

    private final List<MetricSample> all;
    private final Map<String, List<MetricSample>> byName;

    MetricSampleIndex(List<MetricSample> samples) {
        this.all = samples;
        this.byName = samples.stream().collect(Collectors.groupingBy(MetricSample::name));
    }

    List<MetricSample> all() {
        return all;
    }

    /** All samples exposed under the exact metric name (across label sets); empty when absent. */
    List<MetricSample> named(String name) {
        return byName.getOrDefault(name, List.of());
    }

    /** The {@code _bucket}/{@code _sum}/{@code _count} series of a histogram base name, in one list. */
    List<MetricSample> histogramSeries(String base) {
        var series = new ArrayList<MetricSample>();
        series.addAll(named(base + "_bucket"));
        series.addAll(named(base + "_sum"));
        series.addAll(named(base + "_count"));
        return series;
    }
}
