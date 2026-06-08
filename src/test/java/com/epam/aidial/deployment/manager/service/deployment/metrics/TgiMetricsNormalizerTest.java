package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.model.metrics.EngineFamily;
import com.epam.aidial.deployment.manager.utils.ResourceUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TgiMetricsNormalizerTest {

    private final PrometheusTextParser parser = new PrometheusTextParser();
    private final TgiMetricsNormalizer normalizer = new TgiMetricsNormalizer();

    @Test
    void shouldSupportTgiOnly() {
        assertThat(normalizer.supports(EngineFamily.TGI)).isTrue();
        assertThat(normalizer.supports(EngineFamily.VLLM)).isFalse();
    }

    @Test
    void shouldNormalizeTgiFixtureToUnifiedSchema() {
        var index = new MetricSampleIndex(parser.parse(ResourceUtils.readResource("/metrics-fixtures/tgi.txt")));

        var normalized = normalizer.normalize(index);

        var serving = normalized.serving();
        // TGI exposes no TTFT and no KV-cache gauge — null by design, block still available
        assertThat(serving.ttft()).isNull();
        assertThat(serving.kvCacheUsage()).isNull();

        assertThat(serving.queueDepth()).isEqualTo(2);
        assertThat(serving.runningRequests()).isEqualTo(3);
        assertThat(serving.interTokenLatency()).isNotNull();
        assertThat(serving.interTokenLatency().mean()).isEqualTo(0.02);
        assertThat(serving.interTokenLatency().p50()).isCloseTo(0.013, within(1e-9));

        // No process_start_time_seconds in the fixture → lifetime rates not derivable
        assertThat(serving.promptTokensPerSecond()).isNull();
        assertThat(serving.generationTokensPerSecond()).isNull();

        var operational = normalized.operational();
        assertThat(operational.requestErrorRatio()).isCloseTo(0.05, within(1e-9));
        assertThat(operational.e2eLatency()).isNotNull();
        assertThat(operational.e2eLatency().mean()).isCloseTo(0.9, within(1e-9));
        assertThat(operational.e2eLatency().p50()).isCloseTo(0.5, within(1e-9));
        // p99 rank falls into the +Inf bucket — clamped to the last finite boundary
        assertThat(operational.e2eLatency().p99()).isEqualTo(2.5);

        assertThat(normalized.rawCounters())
                .containsEntry("prompt_tokens_total", 21000.0)
                .containsEntry("generation_tokens_total", 4200.0)
                .containsEntry("request_total", 100.0)
                .containsEntry("request_success_total", 95.0);
    }

}
