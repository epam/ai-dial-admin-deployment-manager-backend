package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.metrics.DistributionSummary;
import com.epam.aidial.deployment.manager.model.metrics.EngineFamily;
import com.epam.aidial.deployment.manager.model.metrics.NormalizedEngineMetrics;
import com.epam.aidial.deployment.manager.model.metrics.OperationalMetrics;
import com.epam.aidial.deployment.manager.model.metrics.ServingMetrics;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * KServe Python ModelServer vocabulary → unified schema. This framework backs non-generative
 * predictors (text classification, embeddings, sklearn, custom), so the generative serving fields
 * (TTFT, inter-token latency, token throughput, KV-cache, queue depth) and the error ratio do not
 * apply and stay {@code null}. The engine-neutral signals are populated instead: {@code requestLatency}
 * from the predictor's {@code request_predict_seconds} histogram and {@code requestsPerSecond} from
 * its lifetime request count.
 *
 * <p>For a chained InferenceService the work is split: pre/post-processing runs on the
 * {@code transformer} pod ({@code request_preprocess_seconds} / {@code request_postprocess_seconds})
 * and inference on the {@code predictor} pod ({@code request_predict_seconds}). The combined
 * end-to-end latency is reported in {@code operational.e2eLatency} — see
 * {@link #endToEndLatency(EngineScrapeContext, DistributionSummary)} for why its percentiles are
 * null when both pods contribute.</p>
 */
@Component
@LogExecution
public class KserveModelServerMetricsNormalizer extends AbstractEngineMetricsNormalizer {

    private static final String PREDICT_LATENCY = "request_predict_seconds";
    private static final String PREPROCESS_LATENCY = "request_preprocess_seconds";
    private static final String POSTPROCESS_LATENCY = "request_postprocess_seconds";
    private static final String COUNT_SUFFIX = "_count";

    @Override
    public boolean supports(EngineFamily family) {
        return family == EngineFamily.KSERVE_MODELSERVER;
    }

    @Override
    public NormalizedEngineMetrics normalize(EngineScrapeContext context) {
        var predictor = context.predictor();
        var now = Instant.now();

        var predictLatency = HistogramSummaries.summarize(predictor, PREDICT_LATENCY);
        var serving = new ServingMetrics(
                null, null, null, null, null, null, null,
                predictLatency,
                MetricSamples.lifetimeRate(predictor, PREDICT_LATENCY + COUNT_SUFFIX, now).orElse(null));

        var operational = new OperationalMetrics(null, endToEndLatency(context, predictLatency));

        return new NormalizedEngineMetrics(serving, operational, rawCounters(context));
    }

    /**
     * End-to-end latency. With a transformer pod, pre/post-processing happens there and inference on
     * the predictor, so the e2e mean is the sum of the three stage means; cumulative histogram
     * percentiles are not additive across independent pods, so they stay {@code null}. Without a
     * transformer (single-pod predictor) e2e is the predict histogram itself, percentiles included.
     */
    private static DistributionSummary endToEndLatency(EngineScrapeContext context, DistributionSummary predictLatency) {
        if (!context.hasTransformer()) {
            return predictLatency;
        }
        var preprocess = HistogramSummaries.summarize(context.transformer(), PREPROCESS_LATENCY);
        var postprocess = HistogramSummaries.summarize(context.transformer(), POSTPROCESS_LATENCY);
        Double mean = sumMeans(preprocess, predictLatency, postprocess);
        if (mean == null) {
            return null;
        }
        long count = predictLatency != null ? predictLatency.count() : 0;
        return new DistributionSummary(mean, null, null, null, count);
    }

    /** Sum of the non-null stage means; {@code null} when no stage exposed a mean. */
    private static Double sumMeans(DistributionSummary... summaries) {
        double total = 0;
        boolean seen = false;
        for (var summary : summaries) {
            if (summary != null && summary.mean() != null) {
                total += summary.mean();
                seen = true;
            }
        }
        return seen ? total : null;
    }

    /**
     * Echoes the lifetime request counts so clients can derive their own rates: predict from the
     * predictor, pre/post from the transformer when present (the predictor's own pre/post are
     * near-zero passthroughs), otherwise from the predictor.
     */
    private Map<String, Double> rawCounters(EngineScrapeContext context) {
        var rawCounters = new LinkedHashMap<String, Double>();
        var stageSource = context.hasTransformer() ? context.transformer() : context.predictor();
        MetricSamples.sum(context.predictor(), PREDICT_LATENCY + COUNT_SUFFIX)
                .ifPresent(value -> rawCounters.put("request_predict_total", value));
        MetricSamples.sum(stageSource, PREPROCESS_LATENCY + COUNT_SUFFIX)
                .ifPresent(value -> rawCounters.put("request_preprocess_total", value));
        MetricSamples.sum(stageSource, POSTPROCESS_LATENCY + COUNT_SUFFIX)
                .ifPresent(value -> rawCounters.put("request_postprocess_total", value));
        return rawCounters;
    }

}
