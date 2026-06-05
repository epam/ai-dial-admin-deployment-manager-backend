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
 * SGLang vocabulary → unified schema (spike §3 SGLang column). {@code sglang:token_usage}
 * (ratio 0..1) is the KV-cache pressure gauge. The error ratio is derivable only when the
 * total-request counter is exposed alongside the aborted counter.
 */
@Component
@LogExecution
public class SglangMetricsNormalizer implements EngineMetricsNormalizer {

    private static final String TTFT = "sglang:time_to_first_token_seconds";
    private static final String INTER_TOKEN_LATENCY = "sglang:inter_token_latency_seconds";
    private static final String E2E_LATENCY = "sglang:e2e_request_latency_seconds";
    private static final String PROMPT_TOKENS = "sglang:prompt_tokens_total";
    private static final String GENERATION_TOKENS = "sglang:generation_tokens_total";
    private static final String QUEUE_REQS = "sglang:num_queue_reqs";
    private static final String RUNNING_REQS = "sglang:num_running_reqs";
    private static final String TOKEN_USAGE = "sglang:token_usage";
    private static final String ABORTED_REQUESTS = "sglang:num_aborted_requests_total";
    private static final String REQUESTS_TOTAL = "sglang:num_requests_total";

    @Override
    public boolean supports(EngineFamily family) {
        return family == EngineFamily.SGLANG;
    }

    @Override
    public NormalizedEngineMetrics normalize(List<MetricSample> samples) {
        var now = Instant.now();

        var serving = new ServingMetrics(
                HistogramSummaries.summarize(samples, TTFT),
                HistogramSummaries.summarize(samples, INTER_TOKEN_LATENCY),
                MetricSamples.lifetimeRate(samples, PROMPT_TOKENS, now).orElse(null),
                MetricSamples.lifetimeRate(samples, GENERATION_TOKENS, now).orElse(null),
                MetricSamples.asInteger(MetricSamples.sum(samples, QUEUE_REQS)),
                MetricSamples.asInteger(MetricSamples.sum(samples, RUNNING_REQS)),
                MetricSamples.sum(samples, TOKEN_USAGE)
                        .map(MetricSamples::clampRatio)
                        .orElse(null));

        var operational = new OperationalMetrics(
                requestErrorRatio(samples),
                HistogramSummaries.summarize(samples, E2E_LATENCY));

        var rawCounters = new HashMap<String, Double>();
        MetricSamples.sum(samples, PROMPT_TOKENS).ifPresent(v -> rawCounters.put("prompt_tokens_total", v));
        MetricSamples.sum(samples, GENERATION_TOKENS).ifPresent(v -> rawCounters.put("generation_tokens_total", v));
        MetricSamples.sum(samples, ABORTED_REQUESTS).ifPresent(v -> rawCounters.put("request_aborted_total", v));
        MetricSamples.sum(samples, REQUESTS_TOTAL).ifPresent(v -> rawCounters.put("request_total", v));

        return new NormalizedEngineMetrics(serving, operational, rawCounters);
    }

    /** Lifetime error ratio: aborted over total, when both counters are exposed. */
    private static Double requestErrorRatio(List<MetricSample> samples) {
        var aborted = MetricSamples.sum(samples, ABORTED_REQUESTS);
        var total = MetricSamples.sum(samples, REQUESTS_TOTAL);
        if (aborted.isEmpty() || total.isEmpty() || total.get() <= 0) {
            return null;
        }
        return MetricSamples.clampRatio(aborted.get() / total.get());
    }

}
