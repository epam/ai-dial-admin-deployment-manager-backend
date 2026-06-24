package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.metrics.EngineFamily;
import com.epam.aidial.deployment.manager.model.metrics.NormalizedEngineMetrics;
import com.epam.aidial.deployment.manager.model.metrics.OperationalMetrics;
import com.epam.aidial.deployment.manager.model.metrics.ServingMetrics;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * TGI vocabulary → unified schema (spike §3 TGI column). TGI exposes no TTFT and no KV-cache
 * gauge — those fields stay {@code null} by design while the serving block remains available.
 * Token totals come from the cumulative {@code _sum} series of TGI's per-request length
 * histograms.
 */
@Component
@LogExecution
public class TgiMetricsNormalizer extends AbstractEngineMetricsNormalizer {

    private static final String INTER_TOKEN_LATENCY = "tgi_request_mean_time_per_token_duration";
    private static final String E2E_LATENCY = "tgi_request_duration";
    private static final String INPUT_LENGTH = "tgi_request_input_length";
    private static final String GENERATED_TOKENS = "tgi_request_generated_tokens";
    private static final String QUEUE_SIZE = "tgi_queue_size";
    private static final String BATCH_CURRENT_SIZE = "tgi_batch_current_size";
    private static final String REQUEST_COUNT = "tgi_request_count";
    private static final String REQUEST_SUCCESS = "tgi_request_success";

    @Override
    public boolean supports(EngineFamily family) {
        return family == EngineFamily.TGI;
    }

    @Override
    public NormalizedEngineMetrics normalize(EngineScrapeContext context) {
        var index = context.predictor();
        var now = Instant.now();

        var serving = new ServingMetrics(
                null,
                HistogramSummaries.summarize(index, INTER_TOKEN_LATENCY),
                MetricSamples.lifetimeRate(index, INPUT_LENGTH + "_sum", now).orElse(null),
                MetricSamples.lifetimeRate(index, GENERATED_TOKENS + "_sum", now).orElse(null),
                MetricSamples.asInteger(MetricSamples.sum(index, QUEUE_SIZE)),
                MetricSamples.asInteger(MetricSamples.sum(index, BATCH_CURRENT_SIZE)),
                null,
                null,
                null);

        var operational = new OperationalMetrics(
                requestErrorRatio(index),
                HistogramSummaries.summarize(index, E2E_LATENCY));

        var rawCounters = rawCounters(index, rawCounterSources(
                "prompt_tokens_total", INPUT_LENGTH + "_sum",
                "generation_tokens_total", GENERATED_TOKENS + "_sum",
                "request_total", REQUEST_COUNT,
                "request_success_total", REQUEST_SUCCESS));

        return new NormalizedEngineMetrics(serving, operational, rawCounters);
    }

    /** Lifetime error ratio: failed requests ({@code count - success}) over all requests. */
    private static Double requestErrorRatio(MetricSampleIndex index) {
        Optional<Double> total = MetricSamples.sum(index, REQUEST_COUNT);
        Optional<Double> success = MetricSamples.sum(index, REQUEST_SUCCESS);
        if (total.isEmpty() || success.isEmpty()) {
            return null;
        }
        return clampedRatio(total.get() - success.get(), total.get());
    }

}
