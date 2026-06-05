package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.metrics.EngineFamily;
import com.epam.aidial.deployment.manager.model.metrics.MetricSample;
import com.epam.aidial.deployment.manager.model.metrics.NormalizedEngineMetrics;
import com.epam.aidial.deployment.manager.model.metrics.OperationalMetrics;
import com.epam.aidial.deployment.manager.model.metrics.ServingMetrics;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;

/**
 * vLLM vocabulary → unified schema (spike §3 vLLM column). Accepts both V0 and V1 names where
 * they drifted (verified against a live dev-cluster vLLM V1 pod): KV-cache gauge
 * {@code vllm:gpu_cache_usage_perc} → {@code vllm:kv_cache_usage_perc}, inter-token latency
 * histogram {@code vllm:time_per_output_token_seconds} → {@code vllm:inter_token_latency_seconds}.
 * Also reused by {@link NimMetricsNormalizer} for LLM NIMs, which expose vLLM-style names.
 */
@Component
@LogExecution
public class VllmMetricsNormalizer implements EngineMetricsNormalizer {

    private static final String TTFT = "vllm:time_to_first_token_seconds";
    private static final String INTER_TOKEN_LATENCY_V0 = "vllm:time_per_output_token_seconds";
    private static final String INTER_TOKEN_LATENCY_V1 = "vllm:inter_token_latency_seconds";
    private static final String E2E_LATENCY = "vllm:e2e_request_latency_seconds";
    private static final String PROMPT_TOKENS = "vllm:prompt_tokens_total";
    private static final String GENERATION_TOKENS = "vllm:generation_tokens_total";
    private static final String REQUESTS_WAITING = "vllm:num_requests_waiting";
    private static final String REQUESTS_RUNNING = "vllm:num_requests_running";
    private static final String KV_CACHE_USAGE_V0 = "vllm:gpu_cache_usage_perc";
    private static final String KV_CACHE_USAGE_V1 = "vllm:kv_cache_usage_perc";
    private static final String REQUEST_SUCCESS = "vllm:request_success_total";
    private static final String FINISHED_REASON_LABEL = "finished_reason";
    private static final String FINISHED_REASON_ABORT = "abort";
    private static final String FINISHED_REASON_ERROR = "error";

    @Override
    public boolean supports(EngineFamily family) {
        return family == EngineFamily.VLLM;
    }

    @Override
    public NormalizedEngineMetrics normalize(List<MetricSample> samples) {
        var now = Instant.now();

        var interTokenLatency = HistogramSummaries.summarize(samples, INTER_TOKEN_LATENCY_V0);
        if (interTokenLatency == null) {
            interTokenLatency = HistogramSummaries.summarize(samples, INTER_TOKEN_LATENCY_V1);
        }

        var serving = new ServingMetrics(
                HistogramSummaries.summarize(samples, TTFT),
                interTokenLatency,
                MetricSamples.lifetimeRate(samples, PROMPT_TOKENS, now).orElse(null),
                MetricSamples.lifetimeRate(samples, GENERATION_TOKENS, now).orElse(null),
                MetricSamples.asInteger(MetricSamples.sum(samples, REQUESTS_WAITING)),
                MetricSamples.asInteger(MetricSamples.sum(samples, REQUESTS_RUNNING)),
                MetricSamples.sum(samples, KV_CACHE_USAGE_V0, KV_CACHE_USAGE_V1)
                        .map(MetricSamples::clampRatio)
                        .orElse(null));

        var operational = new OperationalMetrics(
                requestErrorRatio(samples),
                HistogramSummaries.summarize(samples, E2E_LATENCY));

        var rawCounters = new HashMap<String, Double>();
        MetricSamples.sum(samples, PROMPT_TOKENS).ifPresent(v -> rawCounters.put("prompt_tokens_total", v));
        MetricSamples.sum(samples, GENERATION_TOKENS).ifPresent(v -> rawCounters.put("generation_tokens_total", v));
        MetricSamples.sum(samples, REQUEST_SUCCESS).ifPresent(v -> rawCounters.put("request_success_total", v));

        return new NormalizedEngineMetrics(serving, operational, rawCounters);
    }

    /**
     * Lifetime error ratio derived from {@code vllm:request_success_total} broken down by
     * {@code finished_reason}: aborted/errored finishes over all finishes (vLLM V1 added the
     * explicit {@code error} reason alongside V0's {@code abort}).
     */
    private static Double requestErrorRatio(List<MetricSample> samples) {
        var total = MetricSamples.sum(samples, REQUEST_SUCCESS);
        if (total.isEmpty() || total.get() <= 0) {
            return null;
        }
        double failed = MetricSamples.sumWithLabel(samples, REQUEST_SUCCESS, FINISHED_REASON_LABEL, FINISHED_REASON_ABORT)
                .orElse(0.0)
                + MetricSamples.sumWithLabel(samples, REQUEST_SUCCESS, FINISHED_REASON_LABEL, FINISHED_REASON_ERROR)
                .orElse(0.0);
        return MetricSamples.clampRatio(failed / total.get());
    }

}
