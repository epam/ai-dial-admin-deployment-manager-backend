package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.model.metrics.MetricSample;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HistogramSummariesTest {

    private static final String BASE = "ttft_seconds";

    @Test
    void shouldApproximatePercentilesFromCumulativeBuckets() {
        var samples = histogram(Map.of("0.25", 100.0, "0.5", 180.0, "1.0", 196.0, "+Inf", 200.0), 80.0, 200.0);

        var summary = HistogramSummaries.summarize(samples, BASE);

        assertThat(summary).isNotNull();
        assertThat(summary.count()).isEqualTo(200);
        assertThat(summary.mean()).isEqualTo(0.4);
        assertThat(summary.p50()).isCloseTo(0.25, within(1e-9));
        assertThat(summary.p95()).isCloseTo(0.8125, within(1e-9));
        // p99 rank falls into the +Inf bucket — clamped to the last finite boundary
        assertThat(summary.p99()).isEqualTo(1.0);
    }

    @Test
    void shouldInterpolateWithinBucket() {
        var samples = histogram(Map.of("0.01", 50.0, "0.02", 150.0, "0.05", 200.0, "+Inf", 200.0), 4.0, 200.0);

        var summary = HistogramSummaries.summarize(samples, BASE);

        assertThat(summary).isNotNull();
        assertThat(summary.p50()).isCloseTo(0.015, within(1e-9));
        assertThat(summary.p95()).isCloseTo(0.044, within(1e-9));
        assertThat(summary.p99()).isCloseTo(0.0488, within(1e-9));
    }

    @Test
    void shouldAggregateAcrossLabelSets() {
        var samples = List.of(
                bucket("0.5", 10.0, "model-a"),
                bucket("+Inf", 10.0, "model-a"),
                bucket("0.5", 30.0, "model-b"),
                bucket("+Inf", 30.0, "model-b"),
                new MetricSample(BASE + "_sum", Map.of("model_name", "model-a"), 2.0),
                new MetricSample(BASE + "_sum", Map.of("model_name", "model-b"), 6.0),
                new MetricSample(BASE + "_count", Map.of("model_name", "model-a"), 10.0),
                new MetricSample(BASE + "_count", Map.of("model_name", "model-b"), 30.0));

        var summary = HistogramSummaries.summarize(samples, BASE);

        assertThat(summary).isNotNull();
        assertThat(summary.count()).isEqualTo(40);
        assertThat(summary.mean()).isEqualTo(0.2);
    }

    @Test
    void shouldReturnNull_whenHistogramAbsent() {
        assertThat(HistogramSummaries.summarize(List.of(), BASE)).isNull();
        assertThat(HistogramSummaries.summarize(
                List.of(new MetricSample("other_metric", Map.of(), 1.0)), BASE)).isNull();
    }

    @Test
    void shouldReturnNull_whenNoObservations() {
        var samples = histogram(Map.of("0.5", 0.0, "+Inf", 0.0), 0.0, 0.0);

        assertThat(HistogramSummaries.summarize(samples, BASE)).isNull();
    }

    private static List<MetricSample> histogram(Map<String, Double> buckets, double sum, double count) {
        var samples = new ArrayList<MetricSample>();
        buckets.forEach((le, value) -> samples.add(bucket(le, value, "m")));
        samples.add(new MetricSample(BASE + "_sum", Map.of(), sum));
        samples.add(new MetricSample(BASE + "_count", Map.of(), count));
        return samples;
    }

    private static MetricSample bucket(String le, double value, String model) {
        return new MetricSample(BASE + "_bucket", Map.of("le", le, "model_name", model), value);
    }

}
