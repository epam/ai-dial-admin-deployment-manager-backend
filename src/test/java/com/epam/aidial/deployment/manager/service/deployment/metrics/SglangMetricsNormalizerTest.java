package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.model.metrics.EngineFamily;
import com.epam.aidial.deployment.manager.utils.ResourceUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SglangMetricsNormalizerTest {

    private final PrometheusTextParser parser = new PrometheusTextParser();
    private final SglangMetricsNormalizer normalizer = new SglangMetricsNormalizer();

    @Test
    void shouldSupportSglangOnly() {
        assertThat(normalizer.supports(EngineFamily.SGLANG)).isTrue();
        assertThat(normalizer.supports(EngineFamily.VLLM)).isFalse();
    }

    @Test
    void shouldNormalizeSglangFixtureToUnifiedSchema() {
        var index = new MetricSampleIndex(parser.parse(ResourceUtils.readResource("/metrics-fixtures/sglang.txt")));

        var normalized = normalizer.normalize(index);

        var serving = normalized.serving();
        assertThat(serving.queueDepth()).isEqualTo(1);
        assertThat(serving.runningRequests()).isEqualTo(4);
        assertThat(serving.kvCacheUsage()).isEqualTo(0.61);

        assertThat(serving.ttft()).isNotNull();
        assertThat(serving.ttft().count()).isEqualTo(150);
        assertThat(serving.ttft().mean()).isCloseTo(0.3, within(1e-9));
        assertThat(serving.ttft().p50()).isCloseTo(0.3, within(1e-9));
        assertThat(serving.ttft().p95()).isCloseTo(0.875, within(1e-9));
        assertThat(serving.ttft().p99()).isCloseTo(0.975, within(1e-9));

        assertThat(serving.interTokenLatency()).isNotNull();
        assertThat(serving.promptTokensPerSecond()).isPositive();
        assertThat(serving.generationTokensPerSecond()).isPositive();

        var operational = normalized.operational();
        assertThat(operational.requestErrorRatio()).isCloseTo(0.02, within(1e-9));
        assertThat(operational.e2eLatency()).isNotNull();
        assertThat(operational.e2eLatency().mean()).isCloseTo(2.5, within(1e-9));

        assertThat(normalized.rawCounters())
                .containsEntry("prompt_tokens_total", 30000.0)
                .containsEntry("generation_tokens_total", 5000.0)
                .containsEntry("request_aborted_total", 3.0)
                .containsEntry("request_total", 150.0);
    }

}
