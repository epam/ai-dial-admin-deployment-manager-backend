package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.model.metrics.EngineFamily;
import com.epam.aidial.deployment.manager.utils.ResourceUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class KserveModelServerMetricsNormalizerTest {

    private final PrometheusTextParser parser = new PrometheusTextParser();
    private final KserveModelServerMetricsNormalizer normalizer = new KserveModelServerMetricsNormalizer();

    @Test
    void shouldSupportKserveModelServerOnly() {
        assertThat(normalizer.supports(EngineFamily.KSERVE_MODELSERVER)).isTrue();
        assertThat(normalizer.supports(EngineFamily.VLLM)).isFalse();
        assertThat(normalizer.supports(EngineFamily.UNKNOWN)).isFalse();
    }

    @Test
    void shouldCombinePredictorAndTransformerIntoEndToEndLatency() {
        var context = new EngineScrapeContext(
                fixture("/metrics-fixtures/kserve-modelserver-predictor.txt"),
                fixture("/metrics-fixtures/kserve-modelserver-transformer.txt"));

        var normalized = normalizer.normalize(context);

        var serving = normalized.serving();
        // predict latency is the predictor's inference histogram (sum 20 / count 100)
        assertThat(serving.requestLatency()).isNotNull();
        assertThat(serving.requestLatency().mean()).isCloseTo(0.2, within(1e-9));
        assertThat(serving.requestLatency().count()).isEqualTo(100);
        assertThat(serving.requestsPerSecond()).isNotNull().isPositive();
        // generative signals do not apply to a non-generative predictor
        assertThat(serving.ttft()).isNull();
        assertThat(serving.interTokenLatency()).isNull();
        assertThat(serving.promptTokensPerSecond()).isNull();
        assertThat(serving.kvCacheUsage()).isNull();
        assertThat(serving.queueDepth()).isNull();

        var operational = normalized.operational();
        assertThat(operational.requestErrorRatio()).isNull();
        // 0.05 (pre, transformer) + 0.2 (predict, predictor) + 0.03 (post, transformer)
        assertThat(operational.e2eLatency().mean()).isCloseTo(0.28, within(1e-9));
        assertThat(operational.e2eLatency().count()).isEqualTo(100);
        assertThat(operational.e2eLatency().p50()).isNull();
        assertThat(operational.e2eLatency().p95()).isNull();

        assertThat(normalized.rawCounters())
                .containsEntry("request_predict_total", 100.0)
                .containsEntry("request_preprocess_total", 100.0)
                .containsEntry("request_postprocess_total", 100.0);
    }

    @Test
    void shouldUsePredictHistogramAsEndToEnd_whenNoTransformer() {
        var normalized = normalizer.normalize(
                EngineScrapeContext.of(fixture("/metrics-fixtures/kserve-modelserver-predictor.txt")));

        var e2e = normalized.operational().e2eLatency();
        // single-pod predictor: e2e is the predict histogram itself, percentiles included
        assertThat(e2e.mean()).isCloseTo(0.2, within(1e-9));
        assertThat(e2e.count()).isEqualTo(100);
        assertThat(e2e.p50()).isNotNull();
        // the predictor fixture exposes no pre/post series, so those counters are not echoed
        assertThat(normalized.rawCounters())
                .containsOnlyKeys("request_predict_total");
    }

    @Test
    void shouldLeaveFieldsNull_whenIdleWithNoSeries() {
        var normalized = normalizer.normalize(EngineScrapeContext.of(new MetricSampleIndex(List.of())));

        var serving = normalized.serving();
        assertThat(serving.requestLatency()).isNull();
        assertThat(serving.requestsPerSecond()).isNull();
        assertThat(normalized.operational().e2eLatency()).isNull();
        assertThat(normalized.operational().requestErrorRatio()).isNull();
        assertThat(normalized.rawCounters()).isEmpty();
    }

    private MetricSampleIndex fixture(String resource) {
        return new MetricSampleIndex(parser.parse(ResourceUtils.readResource(resource)).samples());
    }

}
