package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.model.metrics.EngineFamily;
import com.epam.aidial.deployment.manager.model.metrics.NormalizedEngineMetrics;
import com.epam.aidial.deployment.manager.utils.ResourceUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class VllmMetricsNormalizerTest {

    private final PrometheusTextParser parser = new PrometheusTextParser();
    private final VllmMetricsNormalizer normalizer = new VllmMetricsNormalizer();

    @Test
    void shouldSupportVllmOnly() {
        assertThat(normalizer.supports(EngineFamily.VLLM)).isTrue();
        assertThat(normalizer.supports(EngineFamily.TGI)).isFalse();
        assertThat(normalizer.supports(EngineFamily.UNKNOWN)).isFalse();
    }

    @Test
    void shouldNormalizeVllmV0FixtureToUnifiedSchema() {
        var samples = fixture("/metrics-fixtures/vllm-v0.txt");

        var normalized = normalize(samples);

        var serving = normalized.serving();
        assertThat(serving.queueDepth()).isZero();
        assertThat(serving.runningRequests()).isEqualTo(1);
        assertThat(serving.kvCacheUsage()).isEqualTo(0.27);

        assertThat(serving.ttft()).isNotNull();
        assertThat(serving.ttft().count()).isEqualTo(200);
        assertThat(serving.ttft().mean()).isEqualTo(0.4);
        assertThat(serving.ttft().p50()).isCloseTo(0.25, within(1e-9));
        assertThat(serving.ttft().p95()).isCloseTo(0.8125, within(1e-9));
        assertThat(serving.ttft().p99()).isEqualTo(1.0);

        assertThat(serving.interTokenLatency()).isNotNull();
        assertThat(serving.interTokenLatency().mean()).isEqualTo(0.02);

        // process_start_time_seconds is present in the fixture → lifetime rates computable
        assertThat(serving.promptTokensPerSecond()).isPositive();
        assertThat(serving.generationTokensPerSecond()).isPositive();

        var operational = normalized.operational();
        assertThat(operational.requestErrorRatio()).isCloseTo(0.02, within(1e-9));
        assertThat(operational.e2eLatency()).isNotNull();
        assertThat(operational.e2eLatency().mean()).isEqualTo(3.0);
        assertThat(operational.e2eLatency().p50()).isCloseTo(2.0, within(1e-9));
        assertThat(operational.e2eLatency().p95()).isCloseTo(7.5, within(1e-9));
        assertThat(operational.e2eLatency().p99()).isCloseTo(9.5, within(1e-9));

        assertThat(normalized.rawCounters())
                .containsEntry("prompt_tokens_total", 52800.0)
                .containsEntry("generation_tokens_total", 4810.0)
                .containsEntry("request_success_total", 200.0);
    }

    @Test
    void shouldNormalizeRealV1Capture() {
        // vllm.txt is a real dev-cluster vLLM V1 capture: inter-token latency is exposed under
        // the renamed vllm:inter_token_latency_seconds, KV cache under vllm:kv_cache_usage_perc
        var normalized = normalize(fixture("/metrics-fixtures/vllm.txt"));

        var serving = normalized.serving();
        assertThat(serving.queueDepth()).isZero();
        assertThat(serving.runningRequests()).isZero();
        assertThat(serving.kvCacheUsage()).isEqualTo(0.0);

        assertThat(serving.ttft()).isNotNull();
        assertThat(serving.ttft().count()).isEqualTo(14);
        assertThat(serving.ttft().mean()).isCloseTo(4.661037445068359 / 14, within(1e-9));

        // V1 rename picked up via the fallback name
        assertThat(serving.interTokenLatency()).isNotNull();
        assertThat(serving.interTokenLatency().count()).isEqualTo(690);
        assertThat(serving.interTokenLatency().mean()).isCloseTo(22.052519921035127 / 690, within(1e-9));

        // process_start_time_seconds is exposed → lifetime rates computable
        assertThat(serving.promptTokensPerSecond()).isPositive();
        assertThat(serving.generationTokensPerSecond()).isPositive();

        var operational = normalized.operational();
        assertThat(operational.requestErrorRatio()).isEqualTo(0.0);
        assertThat(operational.e2eLatency()).isNotNull();
        assertThat(operational.e2eLatency().count()).isEqualTo(14);
        assertThat(operational.e2eLatency().mean()).isCloseTo(26.712053537368774 / 14, within(1e-9));

        assertThat(normalized.rawCounters())
                .containsEntry("prompt_tokens_total", 34304.0)
                .containsEntry("generation_tokens_total", 704.0)
                .containsEntry("request_success_total", 14.0);
    }

    @Test
    void shouldAcceptV1KvCacheRename() {
        var samples = fixture("/metrics-fixtures/vllm-v1.txt");

        var serving = normalize(samples).serving();

        assertThat(serving.kvCacheUsage()).isEqualTo(0.42);
        assertThat(serving.runningRequests()).isEqualTo(2);
        assertThat(serving.queueDepth()).isEqualTo(1);
    }

    @Test
    void shouldLeaveFieldsNull_whenMetricsAbsent() {
        var normalized = normalize(new MetricSampleIndex(List.of()));

        var serving = normalized.serving();
        assertThat(serving.ttft()).isNull();
        assertThat(serving.kvCacheUsage()).isNull();
        assertThat(serving.queueDepth()).isNull();
        assertThat(serving.promptTokensPerSecond()).isNull();
        assertThat(normalized.operational().requestErrorRatio()).isNull();
        assertThat(normalized.rawCounters()).isEmpty();
    }

    @Test
    void shouldLeaveRatesNull_whenProcessStartTimeAbsent() {
        // vllm-v1 fixture has counters but no process_start_time_seconds
        var serving = normalize(fixture("/metrics-fixtures/vllm-v1.txt")).serving();

        assertThat(serving.promptTokensPerSecond()).isNull();
        assertThat(serving.generationTokensPerSecond()).isNull();
    }

    private NormalizedEngineMetrics normalize(MetricSampleIndex index) {
        return normalizer.normalize(EngineScrapeContext.of(index));
    }

    private MetricSampleIndex fixture(String resource) {
        return new MetricSampleIndex(parser.parse(ResourceUtils.readResource(resource)).samples());
    }

}
