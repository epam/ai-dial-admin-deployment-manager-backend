package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.metrics.EngineFamily;
import com.epam.aidial.deployment.manager.model.metrics.NormalizedEngineMetrics;
import com.epam.aidial.deployment.manager.model.metrics.OperationalMetrics;
import com.epam.aidial.deployment.manager.model.metrics.ServingMetrics;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * SGLang vocabulary → unified schema (spike §3 SGLang column). {@code sglang:token_usage}
 * (ratio 0..1) is the KV-cache pressure gauge. The error ratio is derivable only when the
 * total-request counter is exposed alongside the aborted counter.
 */
@Component
@LogExecution
public class SglangMetricsNormalizer extends AbstractEngineMetricsNormalizer {

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
    public NormalizedEngineMetrics normalize(EngineScrapeContext context) {
        var index = context.predictor();
        var now = Instant.now();

        var serving = new ServingMetrics(
                HistogramSummaries.summarize(index, TTFT),
                HistogramSummaries.summarize(index, INTER_TOKEN_LATENCY),
                MetricSamples.lifetimeRate(index, PROMPT_TOKENS, now).orElse(null),
                MetricSamples.lifetimeRate(index, GENERATION_TOKENS, now).orElse(null),
                MetricSamples.asInteger(MetricSamples.sum(index, QUEUE_REQS)),
                MetricSamples.asInteger(MetricSamples.sum(index, RUNNING_REQS)),
                MetricSamples.avg(index, TOKEN_USAGE)
                        .map(MetricSamples::clampRatio)
                        .orElse(null),
                null,
                null);

        var operational = new OperationalMetrics(
                requestErrorRatio(index),
                HistogramSummaries.summarize(index, E2E_LATENCY));

        var rawCounters = rawCounters(index, rawCounterSources(
                "prompt_tokens_total", PROMPT_TOKENS,
                "generation_tokens_total", GENERATION_TOKENS,
                "request_aborted_total", ABORTED_REQUESTS,
                "request_total", REQUESTS_TOTAL));

        return new NormalizedEngineMetrics(serving, operational, rawCounters);
    }

    /** Lifetime error ratio: aborted over total, when both counters are exposed. */
    private static Double requestErrorRatio(MetricSampleIndex index) {
        var aborted = MetricSamples.sum(index, ABORTED_REQUESTS);
        var total = MetricSamples.sum(index, REQUESTS_TOTAL);
        if (aborted.isEmpty() || total.isEmpty()) {
            return null;
        }
        return clampedRatio(aborted.get(), total.get());
    }

}
