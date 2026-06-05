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
import java.util.Optional;

/**
 * TGI vocabulary → unified schema (spike §3 TGI column). TGI exposes no TTFT and no KV-cache
 * gauge — those fields stay {@code null} by design while the serving block remains available.
 * Token totals come from the cumulative {@code _sum} series of TGI's per-request length
 * histograms.
 */
@Component
@LogExecution
public class TgiMetricsNormalizer implements EngineMetricsNormalizer {

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
    public NormalizedEngineMetrics normalize(List<MetricSample> samples) {
        var now = Instant.now();

        var serving = new ServingMetrics(
                null,
                HistogramSummaries.summarize(samples, INTER_TOKEN_LATENCY),
                MetricSamples.lifetimeRate(samples, INPUT_LENGTH + "_sum", now).orElse(null),
                MetricSamples.lifetimeRate(samples, GENERATED_TOKENS + "_sum", now).orElse(null),
                MetricSamples.asInteger(MetricSamples.sum(samples, QUEUE_SIZE)),
                MetricSamples.asInteger(MetricSamples.sum(samples, BATCH_CURRENT_SIZE)),
                null);

        var operational = new OperationalMetrics(
                requestErrorRatio(samples),
                HistogramSummaries.summarize(samples, E2E_LATENCY));

        var rawCounters = new HashMap<String, Double>();
        MetricSamples.sum(samples, INPUT_LENGTH + "_sum").ifPresent(v -> rawCounters.put("prompt_tokens_total", v));
        MetricSamples.sum(samples, GENERATED_TOKENS + "_sum").ifPresent(v -> rawCounters.put("generation_tokens_total", v));
        MetricSamples.sum(samples, REQUEST_COUNT).ifPresent(v -> rawCounters.put("request_total", v));
        MetricSamples.sum(samples, REQUEST_SUCCESS).ifPresent(v -> rawCounters.put("request_success_total", v));

        return new NormalizedEngineMetrics(serving, operational, rawCounters);
    }

    /** Lifetime error ratio: failed requests ({@code count - success}) over all requests. */
    private static Double requestErrorRatio(List<MetricSample> samples) {
        Optional<Double> total = MetricSamples.sum(samples, REQUEST_COUNT);
        Optional<Double> success = MetricSamples.sum(samples, REQUEST_SUCCESS);
        if (total.isEmpty() || success.isEmpty() || total.get() <= 0) {
            return null;
        }
        return MetricSamples.clampRatio((total.get() - success.get()) / total.get());
    }

}
