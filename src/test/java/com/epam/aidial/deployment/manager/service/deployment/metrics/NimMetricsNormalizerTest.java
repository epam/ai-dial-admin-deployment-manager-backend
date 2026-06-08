package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.model.metrics.EngineFamily;
import com.epam.aidial.deployment.manager.utils.ResourceUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class NimMetricsNormalizerTest {

    private final PrometheusTextParser parser = new PrometheusTextParser();
    private final NimMetricsNormalizer normalizer = new NimMetricsNormalizer(new VllmMetricsNormalizer());

    @Test
    void shouldSupportNimOnly() {
        assertThat(normalizer.supports(EngineFamily.NIM)).isTrue();
        assertThat(normalizer.supports(EngineFamily.VLLM)).isFalse();
    }

    @Test
    void shouldNormalizeLlmNimThroughVllmRules() {
        // nim-llm.txt is a real dev-cluster LLM NIM /v1/metrics capture: bare vLLM-style names
        // (no "vllm:" prefix) — exercises the aliasing path — plus explicit
        // request_success_total/request_failure_total outcome counters
        var samples = parser.parse(ResourceUtils.readResource("/metrics-fixtures/nim-llm.txt"));

        var normalized = normalizer.normalize(samples);

        var serving = normalized.serving();
        assertThat(serving.runningRequests()).isZero();
        assertThat(serving.queueDepth()).isZero();
        assertThat(serving.kvCacheUsage()).isEqualTo(0.0);
        assertThat(serving.ttft()).isNotNull();
        assertThat(serving.ttft().count()).isEqualTo(1);
        assertThat(serving.ttft().mean()).isCloseTo(8.493255376815796, within(1e-9));
        assertThat(serving.interTokenLatency()).isNotNull();
        assertThat(serving.interTokenLatency().count()).isEqualTo(15);
        assertThat(serving.interTokenLatency().mean()).isCloseTo(0.19932079315185547 / 15, within(1e-9));
        // no process_start_time_seconds in the NIM exposition → lifetime rates not derivable
        assertThat(serving.promptTokensPerSecond()).isNull();

        // error ratio from the explicit NIM outcome counters: failure / (success + failure)
        assertThat(normalized.operational().requestErrorRatio()).isEqualTo(0.0);
        assertThat(normalized.operational().e2eLatency()).isNotNull();
        assertThat(normalized.operational().e2eLatency().count()).isEqualTo(1);
        assertThat(normalized.operational().e2eLatency().mean()).isCloseTo(8.692916631698608, within(1e-9));

        assertThat(normalized.rawCounters())
                .containsEntry("prompt_tokens_total", 3.0)
                .containsEntry("generation_tokens_total", 16.0)
                .containsEntry("request_success_total", 1.0)
                .containsEntry("request_failure_total", 0.0);
    }

    @Test
    void shouldDeriveErrorRatioFromNimOutcomeCounters() {
        var samples = parser.parse("""
                request_success_total{model_name="m"} 9.0
                request_failure_total{model_name="m"} 1.0
                """);

        var normalized = normalizer.normalize(samples);

        assertThat(normalized.operational().requestErrorRatio()).isCloseTo(0.1, within(1e-9));
        assertThat(normalized.rawCounters()).containsEntry("request_failure_total", 1.0);
    }

    @Test
    void shouldClassifyHybridExpositionAsLlmNim_notTriton() {
        // Bare vLLM-style serving names alongside nv_* GPU series and NO "vllm:" prefix: positive
        // detection must still classify this as an LLM NIM, not silently empty the serving block.
        var samples = parser.parse("""
                time_to_first_token_seconds_bucket{le="0.5"} 3
                time_to_first_token_seconds_bucket{le="+Inf"} 3
                time_to_first_token_seconds_sum 1.2
                time_to_first_token_seconds_count 3
                num_requests_running 2
                request_success_total 10
                request_failure_total 0
                nv_gpu_utilization{gpu_uuid="GPU-0"} 0.42
                """);

        var normalized = normalizer.normalize(samples);

        assertThat(normalized.serving().ttft()).isNotNull();
        assertThat(normalized.serving().ttft().count()).isEqualTo(3);
        assertThat(normalized.serving().runningRequests()).isEqualTo(2);
        assertThat(normalized.operational().requestErrorRatio()).isEqualTo(0.0);
        assertThat(normalized.rawCounters()).containsEntry("request_failure_total", 0.0);
    }

    @Test
    void shouldReportPartialMetricsForTritonNim() {
        var samples = parser.parse(ResourceUtils.readResource("/metrics-fixtures/nim-triton.txt"));

        var normalized = normalizer.normalize(samples);

        // Triton NIMs expose no serving-quality metrics — honestly absent, not guessed
        var serving = normalized.serving();
        assertThat(serving.ttft()).isNull();
        assertThat(serving.interTokenLatency()).isNull();
        assertThat(serving.kvCacheUsage()).isNull();
        assertThat(serving.queueDepth()).isNull();
        assertThat(serving.runningRequests()).isNull();

        var operational = normalized.operational();
        assertThat(operational.requestErrorRatio()).isCloseTo(0.01, within(1e-9));
        assertThat(operational.e2eLatency()).isNotNull();
        assertThat(operational.e2eLatency().mean()).isCloseTo(5.0, within(1e-9));
        assertThat(operational.e2eLatency().count()).isEqualTo(1000);
        assertThat(operational.e2eLatency().p50()).isNull();

        assertThat(normalized.rawCounters())
                .containsEntry("request_total", 1000.0)
                .containsEntry("request_success_total", 990.0);
    }

}
