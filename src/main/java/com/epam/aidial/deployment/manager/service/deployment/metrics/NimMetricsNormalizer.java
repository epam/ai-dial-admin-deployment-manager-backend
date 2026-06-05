package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.metrics.DistributionSummary;
import com.epam.aidial.deployment.manager.model.metrics.EngineFamily;
import com.epam.aidial.deployment.manager.model.metrics.MetricSample;
import com.epam.aidial.deployment.manager.model.metrics.NormalizedEngineMetrics;
import com.epam.aidial.deployment.manager.model.metrics.OperationalMetrics;
import com.epam.aidial.deployment.manager.model.metrics.ServingMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * NIM vocabulary → unified schema (spike §3.4). LLM NIMs (vLLM/TRT-LLM-based) expose vLLM-style
 * names — optionally without the {@code vllm:} prefix — and are delegated to the vLLM mapping
 * rules after aliasing. Triton-based NIMs (embedding/reranking) expose {@code nv_*}; most
 * serving-quality metrics do not exist there and stay {@code null} — what exists is reported,
 * the rest is honestly absent.
 */
@Component
@LogExecution
@RequiredArgsConstructor
public class NimMetricsNormalizer implements EngineMetricsNormalizer {

    private static final String VLLM_PREFIX = "vllm:";
    private static final String TRITON_PREFIX = "nv_";

    /** Bare vLLM-style base names some LLM NIM builds expose without the {@code vllm:} prefix. */
    private static final Set<String> VLLM_STYLE_BASE_NAMES = Set.of(
            "time_to_first_token_seconds",
            "time_per_output_token_seconds",
            "inter_token_latency_seconds",
            "e2e_request_latency_seconds",
            "prompt_tokens_total",
            "generation_tokens_total",
            "num_requests_waiting",
            "num_requests_running",
            "gpu_cache_usage_perc",
            "kv_cache_usage_perc",
            "request_success_total");

    /** LLM NIM explicit outcome counters (verified live) — preferred error-ratio source. */
    private static final String LLM_REQUEST_SUCCESS = "request_success_total";
    private static final String LLM_REQUEST_FAILURE = "request_failure_total";

    private static final String TRITON_INFERENCE_COUNT = "nv_inference_count";
    private static final String TRITON_REQUEST_SUCCESS = "nv_inference_request_success";
    private static final String TRITON_REQUEST_FAILURE = "nv_inference_request_failure";
    private static final String TRITON_REQUEST_DURATION_US = "nv_inference_request_duration_us";

    private final VllmMetricsNormalizer vllmMetricsNormalizer;

    @Override
    public boolean supports(EngineFamily family) {
        return family == EngineFamily.NIM;
    }

    @Override
    public NormalizedEngineMetrics normalize(List<MetricSample> samples) {
        if (isTriton(samples)) {
            return normalizeTriton(samples);
        }
        var normalized = vllmMetricsNormalizer.normalize(aliasVllmStyleNames(samples));
        return withLlmNimOutcomeCounters(normalized, samples);
    }

    /**
     * LLM NIMs expose explicit {@code request_success_total}/{@code request_failure_total}
     * counters (their {@code request_success_total} carries no {@code finished_reason} labels,
     * unlike upstream vLLM) — when present, they are the authoritative error-ratio source and
     * the failure counter is echoed raw.
     */
    private static NormalizedEngineMetrics withLlmNimOutcomeCounters(NormalizedEngineMetrics normalized,
                                                                     List<MetricSample> samples) {
        var success = MetricSamples.sum(samples, LLM_REQUEST_SUCCESS);
        var failure = MetricSamples.sum(samples, LLM_REQUEST_FAILURE);
        if (success.isEmpty() || failure.isEmpty()) {
            return normalized;
        }
        double total = success.get() + failure.get();
        if (total <= 0) {
            return normalized;
        }
        var operational = new OperationalMetrics(
                MetricSamples.clampRatio(failure.get() / total),
                normalized.operational() == null ? null : normalized.operational().e2eLatency());
        var rawCounters = new HashMap<>(normalized.rawCounters());
        rawCounters.put("request_failure_total", failure.get());
        return new NormalizedEngineMetrics(normalized.serving(), operational, rawCounters);
    }

    private static boolean isTriton(List<MetricSample> samples) {
        boolean hasTriton = false;
        for (var sample : samples) {
            if (sample.name().startsWith(VLLM_PREFIX)) {
                return false;
            }
            if (sample.name().startsWith(TRITON_PREFIX)) {
                hasTriton = true;
            }
        }
        return hasTriton;
    }

    /** Prefixes bare vLLM-style names with {@code vllm:} so the vLLM mapping rules apply as-is. */
    private static List<MetricSample> aliasVllmStyleNames(List<MetricSample> samples) {
        var aliased = new ArrayList<MetricSample>(samples.size());
        for (var sample : samples) {
            var name = sample.name();
            if (!name.startsWith(VLLM_PREFIX) && VLLM_STYLE_BASE_NAMES.contains(stripSeriesSuffix(name))) {
                aliased.add(new MetricSample(VLLM_PREFIX + name, sample.labels(), sample.value()));
            } else {
                aliased.add(sample);
            }
        }
        return aliased;
    }

    private static String stripSeriesSuffix(String name) {
        for (var suffix : List.of("_bucket", "_sum", "_count")) {
            if (name.endsWith(suffix)) {
                return name.substring(0, name.length() - suffix.length());
            }
        }
        return name;
    }

    private static NormalizedEngineMetrics normalizeTriton(List<MetricSample> samples) {
        // Triton exposes no TTFT, token, queue-depth, or KV-cache metrics — the whole serving
        // block is honestly empty rather than guessed.
        var serving = new ServingMetrics(null, null, null, null, null, null, null);
        var operational = new OperationalMetrics(tritonErrorRatio(samples), tritonE2eLatency(samples));

        var rawCounters = new HashMap<String, Double>();
        MetricSamples.sum(samples, TRITON_INFERENCE_COUNT).ifPresent(v -> rawCounters.put("request_total", v));
        MetricSamples.sum(samples, TRITON_REQUEST_SUCCESS).ifPresent(v -> rawCounters.put("request_success_total", v));

        return new NormalizedEngineMetrics(serving, operational, rawCounters);
    }

    private static Double tritonErrorRatio(List<MetricSample> samples) {
        var success = MetricSamples.sum(samples, TRITON_REQUEST_SUCCESS);
        var failure = MetricSamples.sum(samples, TRITON_REQUEST_FAILURE);
        if (success.isEmpty() || failure.isEmpty()) {
            return null;
        }
        double total = success.get() + failure.get();
        if (total <= 0) {
            return null;
        }
        return MetricSamples.clampRatio(failure.get() / total);
    }

    /** Lifetime mean from Triton's cumulative duration counter; no percentiles available. */
    private static DistributionSummary tritonE2eLatency(List<MetricSample> samples) {
        var durationUs = MetricSamples.sum(samples, TRITON_REQUEST_DURATION_US);
        var count = MetricSamples.sum(samples, TRITON_INFERENCE_COUNT);
        if (durationUs.isEmpty() || count.isEmpty() || count.get() <= 0) {
            return null;
        }
        double meanSeconds = durationUs.get() / count.get() / 1_000_000;
        return new DistributionSummary(meanSeconds, null, null, null, count.get().longValue());
    }

}
